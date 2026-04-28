package com.earmo.onlyoffice.integration.model.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmStreamEventResponse(
    String documentId,
    String requestId,
    String sessionId,
    String sessionTitle,
    String assistantMessageId,
    String variantId,
    Integer variantIndex,
    Integer activeVariantIndex,
    String provider,
    String model,
    String delta,
    String reasoningText,
    String assistantText,
    LlmUsageResponse usage,
    String finishReason,
    Map<String, Object> providerResponseMeta,
    String errorCode,
    Instant startedTime,
    Instant finishedTime
) {

  public LlmStreamEventResponse(
      String documentId,
      String requestId,
      String sessionId,
      String sessionTitle,
      String assistantMessageId,
      String provider,
      String model,
      String delta,
      String reasoningText,
      String assistantText,
      LlmUsageResponse usage,
      String finishReason,
      Map<String, Object> providerResponseMeta,
      String errorCode,
      Instant startedTime,
      Instant finishedTime
  ) {
    this(
        documentId,
        requestId,
        sessionId,
        sessionTitle,
        assistantMessageId,
        null,
        null,
        null,
        provider,
        model,
        delta,
        reasoningText,
        assistantText,
        usage,
        finishReason,
        providerResponseMeta,
        errorCode,
        startedTime,
        finishedTime
    );
  }
}
