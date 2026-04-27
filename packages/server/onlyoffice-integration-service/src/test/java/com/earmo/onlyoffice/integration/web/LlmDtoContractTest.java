package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmStreamEventResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmUsageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDtoContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void shouldKeepAssistantTextAssistantMessageIdSessionIdAndRequestIdInResponse() throws Exception {
    LlmRequestStatusResponse response = new LlmRequestStatusResponse(
        "doc-1",
        "request-1",
        "session-1",
        "assistant-1",
        "completed",
        "assistantText body",
        new LlmUsageResponse(10, 20, 30),
        "stop",
        Map.of("model", "fake-gpt"),
        null,
        Instant.parse("2026-04-21T00:00:00Z"),
        Instant.parse("2026-04-21T00:00:01Z")
    );

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("assistantText");
    assertThat(json).contains("assistantMessageId");
    assertThat(json).contains("sessionId");
    assertThat(json).contains("requestId");
    assertThat(json).contains("status");
    assertThat(json).contains("errorCode");
  }

  @Test
  void shouldSerializeReasoningTextAndOmitNullStreamFields() throws Exception {
    LlmStreamEventResponse response = new LlmStreamEventResponse(
        "doc-1",
        "request-1",
        "session-1",
        "自动标题",
        "assistant-1",
        "stub-provider",
        "fake-gpt",
        null,
        "推理片段",
        null,
        null,
        null,
        Map.of(),
        null,
        Instant.parse("2026-04-21T00:00:00Z"),
        null
    );

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"reasoningText\":\"推理片段\"");
    assertThat(json).doesNotContain("\"delta\":null");
    assertThat(json).doesNotContain("\"assistantText\":null");
    assertThat(json).doesNotContain("\"finishedTime\":null");
  }
}
