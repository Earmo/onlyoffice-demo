package com.earmo.onlyoffice.integration.service.llm;

import java.util.List;

public record LlmRuntimeRequest(
    String providerName,
    String baseUrl,
    String apiKey,
    String model,
    long timeoutMillis,
    List<LlmProviderMessage> messages
) {
}
