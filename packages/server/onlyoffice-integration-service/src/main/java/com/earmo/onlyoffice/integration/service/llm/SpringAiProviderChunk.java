package com.earmo.onlyoffice.integration.service.llm;

import java.util.Map;

public record SpringAiProviderChunk(
    String delta,
    String providerRequestId,
    LlmProviderUsage usage,
    String finishReason,
    Map<String, Object> providerResponseMeta
) {
}
