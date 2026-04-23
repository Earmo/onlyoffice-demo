package com.earmo.onlyoffice.integration.model.llm;

import java.util.List;

public record LlmProviderOptionResponse(
    String provider,
    String label,
    String defaultModel,
    List<String> availableModels,
    boolean supportsUpstreamCancel,
    boolean streamEnabled
) {
}
