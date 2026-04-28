package com.earmo.onlyoffice.integration.model.llm;

import java.time.Instant;
import java.util.Map;

public record LlmMessageVariantResponse(
    String variantId,
    int variantIndex,
    String assistantText,
    String status,
    String errorCode,
    String finishReason,
    LlmUsageResponse usage,
    Map<String, Object> providerResponseMeta,
    Instant createdTime
) {
}
