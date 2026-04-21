package com.earmo.onlyoffice.integration.service.llm;

public record LlmProviderCapability(
    String provider,
    String model,
    boolean supportsUpstreamCancel
) {
}
