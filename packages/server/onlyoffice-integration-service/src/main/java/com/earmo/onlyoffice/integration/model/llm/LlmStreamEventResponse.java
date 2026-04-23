package com.earmo.onlyoffice.integration.model.llm;

import java.time.Instant;
import java.util.Map;

public record LlmStreamEventResponse(
    String documentId,
    String requestId,
    String sessionId,
    String assistantMessageId,
    String provider,
    String model,
    String delta,
    String assistantText,
    LlmUsageResponse usage,
    String finishReason,
    Map<String, Object> providerResponseMeta,
    String errorCode,
    Instant startedTime,
    Instant finishedTime
) {
}
