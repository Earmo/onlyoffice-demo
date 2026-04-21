package com.earmo.onlyoffice.integration.service.llm;

import java.util.Map;

public record LlmProviderResponse(
    String providerRequestId,
    String assistantText,
    LlmProviderUsage usage,
    String finishReason,
    Map<String, Object> providerResponseMeta
) {
}
