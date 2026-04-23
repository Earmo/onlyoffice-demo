package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmProviderOptionResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmStreamEventResponse;
import com.earmo.onlyoffice.integration.service.llm.LlmConversationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LlmControllerTest {

  private MockMvc mockMvc;
  private LlmConversationService llmConversationService;

  @BeforeEach
  void setUp() {
    llmConversationService = mock(LlmConversationService.class);
    AccessContextResolver accessContextResolver = mock(AccessContextResolver.class);
    when(accessContextResolver.resolve(any())).thenReturn(new AccessContext(
        "native",
        "native",
        "starter-user",
        "Starter User",
        java.util.Map.of("edit", true),
        "header"
    ));
    mockMvc = MockMvcBuilders.standaloneSetup(new LlmController(llmConversationService, accessContextResolver))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void shouldExposeCapabilityWithRuntimeProviders() throws Exception {
    when(llmConversationService.getCapability(eq("doc-1"), any())).thenReturn(
        new LlmCapabilityResponse(
            "doc-1",
            true,
            null,
            "dashscope",
            "qwen-plus",
            false,
            true,
            "dashscope",
            "qwen-plus",
            List.of(new LlmProviderOptionResponse(
                "dashscope",
                "DashScope",
                "qwen-plus",
                List.of("qwen-plus", "qwen-max"),
                false,
                true
            ))
        )
    );

    mockMvc.perform(get("/api/llm/capability").param("documentId", "doc-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.llmAvailable").value(true))
        .andExpect(jsonPath("$.provider").value("dashscope"))
        .andExpect(jsonPath("$.streamMode").value(true))
        .andExpect(jsonPath("$.defaultProvider").value("dashscope"))
        .andExpect(jsonPath("$.availableProviders[0].availableModels[1]").value("qwen-max"));
  }

  @Test
  void shouldExposeStreamEndpointAsTextEventStream() throws Exception {
    when(llmConversationService.streamMessage(any(), any())).thenAnswer(invocation -> {
      SseEmitter emitter = new SseEmitter(1000L);
      emitter.send(SseEmitter.event()
          .name("request-started")
          .data(new LlmStreamEventResponse(
              "doc-1",
              "req-1",
              "session-1",
              "assistant-1",
              "dashscope",
              "qwen-plus",
              null,
              null,
              null,
              null,
              java.util.Map.of("provider", "dashscope", "model", "qwen-plus"),
              null,
              Instant.parse("2026-04-22T10:00:00Z"),
              null
          )));
      emitter.complete();
      return emitter;
    });

    MvcResult mvcResult = mockMvc.perform(
            post("/api/llm/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"doc-1",
                      "sessionId":"session-1",
                      "provider":"dashscope",
                      "model":"qwen-plus",
                      "question":"流式发送",
                      "selectionSnapshot":{"text":"选区内容","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """)
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(containsString("event:request-started")))
        .andExpect(content().string(containsString("dashscope")));
  }

  @Test
  void shouldReturnJsonWhenStreamRequestFailsBeforeSseStarts() throws Exception {
    when(llmConversationService.streamMessage(any(), any())).thenThrow(new IllegalArgumentException("请求参数不合法"));

    mockMvc.perform(
            post("/api/llm/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"doc-1",
                      "sessionId":"session-1",
                      "provider":"dashscope",
                      "model":"qwen-plus",
                      "question":"流式发送",
                      "selectionSnapshot":{"text":"选区内容","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """)
        )
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("请求参数不合法"));
  }
}
