package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.service.llm.LlmConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    mockMvc = MockMvcBuilders.standaloneSetup(new LlmController(llmConversationService, accessContextResolver)).build();
  }

  @Test
  void shouldExposeCapabilityWhenDisabledOrEnabled() throws Exception {
    when(llmConversationService.getCapability(eq("doc-1"), any())).thenReturn(
        new LlmCapabilityResponse("doc-1", false, "LLM_DISABLED", "openai-compatible", "fake-gpt", false)
    );

    mockMvc.perform(get("/api/llm/capability").param("documentId", "doc-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.llmAvailable").value(false))
        .andExpect(jsonPath("$.disabledReason").value("LLM_DISABLED"));

    when(llmConversationService.getCapability(eq("doc-2"), any())).thenReturn(
        new LlmCapabilityResponse("doc-2", true, null, "openai-compatible", "fake-gpt", false)
    );

    mockMvc.perform(get("/api/llm/capability").param("documentId", "doc-2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.llmAvailable").value(true))
        .andExpect(jsonPath("$.provider").value("openai-compatible"));
  }
}
