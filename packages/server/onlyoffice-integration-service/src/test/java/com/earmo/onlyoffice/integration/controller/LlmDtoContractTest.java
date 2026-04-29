package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.model.llm.LlmMessageResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmMessageVariantResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmStreamEventResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmUsageResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
        "variant-1",
        0,
        0,
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
    assertThat(json).contains("variantId");
    assertThat(json).contains("variantIndex");
    assertThat(json).contains("activeVariantIndex");
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
        "variant-stream-1",
        1,
        1,
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
    assertThat(json).contains("\"variantId\":\"variant-stream-1\"");
    assertThat(json).contains("\"variantIndex\":1");
    assertThat(json).contains("\"activeVariantIndex\":1");
    assertThat(json).doesNotContain("\"delta\":null");
    assertThat(json).doesNotContain("\"assistantText\":null");
    assertThat(json).doesNotContain("\"finishedTime\":null");
  }

  @Test
  void shouldSerializeMessageVariantsAndActiveVariantFields() throws Exception {
    LlmMessageVariantResponse firstVariant = new LlmMessageVariantResponse(
        "variant-message-1",
        0,
        "第一版",
        "completed",
        null,
        "stop",
        new LlmUsageResponse(1, 2, 3),
        Map.of("reasoningContent", "第一版思考"),
        Instant.parse("2026-04-21T00:00:00Z")
    );
    LlmMessageVariantResponse secondVariant = new LlmMessageVariantResponse(
        "variant-message-2",
        1,
        "第二版",
        "completed",
        null,
        "stop",
        new LlmUsageResponse(2, 3, 5),
        Map.of("reasoningContent", "第二版思考"),
        Instant.parse("2026-04-21T00:00:02Z")
    );
    LlmMessageResponse response = new LlmMessageResponse(
        "assistant-1",
        "assistant",
        null,
        "第二版",
        null,
        true,
        null,
        null,
        false,
        "completed",
        null,
        "stop",
        new LlmUsageResponse(2, 3, 5),
        Map.of("reasoningContent", "第二版思考"),
        List.of(firstVariant, secondVariant),
        1,
        Instant.parse("2026-04-21T00:00:02Z")
    );

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"activeVariantIndex\":1");
    assertThat(json).contains("\"variants\":[");
    assertThat(json).contains("\"variantId\":\"variant-message-1\"");
    assertThat(json).contains("\"variantId\":\"variant-message-2\"");
    assertThat(json).contains("\"assistantText\":\"第二版\"");
  }

  @Test
  void shouldDeserializeRegenerateAssistantMessageTarget() throws Exception {
    SendLlmMessageRequest request = objectMapper.readValue("""
        {
          "documentId":"doc-1",
          "sessionId":"session-1",
          "provider":"stub-provider",
          "model":"fake-gpt",
          "question":"重新生成",
          "selectionSnapshot":{"text":"选区","emptySelection":false},
          "headingContext":{"includeHeading":false,"headingId":"","headingText":""},
          "retryConfirmed":true,
          "regenerateAssistantMessageId":"assistant-1"
        }
        """, SendLlmMessageRequest.class);

    assertThat(request.regenerateAssistantMessageId()).isEqualTo("assistant-1");
  }
}
