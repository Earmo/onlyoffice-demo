package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.service.llm.FakeOpenAiCompatibleProviderServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "llm.base-url=http://fake-provider.test",
    "llm.api-key=fake-api-key",
    "llm.model=fake-gpt",
    "llm.enabled=true",
    "llm.feature-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LlmConversationFlowTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RestClient.Builder restClientBuilder;

  private FakeOpenAiCompatibleProviderServer fakeProvider;

  @BeforeEach
  void setUp() {
    // fake provider：通过 MockRestServiceServer 拦截 openai-compatible 请求，不依赖真实外部服务。
    fakeProvider = new FakeOpenAiCompatibleProviderServer(restClientBuilder);
  }

  @AfterEach
  void tearDown() {
    fakeProvider.verify();
  }

  @Test
  void shouldRunCapabilitySessionMessagePollFlow() throws Exception {
    fakeProvider.enqueueSlowSuccess("这是模型返回的建议。", Duration.ofMillis(2200));
    String documentId = createDocument("llm-user", "LLM User");

    mockMvc.perform(get("/api/llm/capability").param("documentId", documentId).headers(TestAccessHeaders.headers("llm-user", "LLM User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.llmAvailable").value(true));

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult sendResult = mockMvc.perform(
            post("/api/llm/messages")
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "question":"SLOW 首轮问题",
                      "selectionSnapshot":{"text":"选区内容","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in_progress"))
        .andReturn();

    String requestId = jsonValue(sendResult, "requestId");

    Thread.sleep(2600L);

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completed"))
        .andExpect(jsonPath("$.assistantText").value("这是模型返回的建议。"));
  }

  @Test
  void shouldKeepCancelledWhenLateSuccessArrivesAndRejectCrossScopeReads() throws Exception {
    fakeProvider.enqueueSlowSuccess("晚到成功结果", Duration.ofMillis(2200));
    String documentId = createDocument("cancel-user", "Cancel User");

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult sendResult = mockMvc.perform(
            post("/api/llm/messages")
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "question":"LATE_SUCCESS 请求取消",
                      "selectionSnapshot":{"text":"","emptySelection":true},
                      "headingContext":{"includeHeading":false,"headingId":"","headingText":""},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String requestId = jsonValue(sendResult, "requestId");

    mockMvc.perform(
            post("/api/llm/requests/{requestId}/cancel", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.errorCode").value("LLM_REQUEST_CANCELLED"));

    Thread.sleep(2600L);

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.assistantText").isEmpty())
        .andExpect(jsonPath("$.errorCode").value("LLM_REQUEST_CANCELLED"));

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("other-user", "Other User"))
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("LLM_SESSION_FORBIDDEN"));
  }

  private String createDocument(String actorUser, String actorName) throws Exception {
    MvcResult result = mockMvc.perform(
            post("/api/documents")
                .headers(TestAccessHeaders.headers(actorUser, actorName))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"llm-flow.docx"}
                    """)
        )
        .andExpect(status().isOk())
        .andReturn();
    return jsonValue(result, "documentId");
  }

  private String jsonValue(MvcResult result, String key) throws Exception {
    String body = result.getResponse().getContentAsString();
    String needle = "\"" + key + "\":\"";
    int start = body.indexOf(needle);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + needle.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }
}
