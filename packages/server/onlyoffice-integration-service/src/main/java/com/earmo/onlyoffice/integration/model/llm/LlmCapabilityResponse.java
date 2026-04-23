package com.earmo.onlyoffice.integration.model.llm;

import java.util.List;

public record LlmCapabilityResponse(
    String documentId,
    boolean llmAvailable,
    String disabledReason,
    String provider,
    String model,
    boolean supportsUpstreamCancel,
    boolean streamMode,
    String defaultProvider,
    String defaultModel,
    List<LlmProviderOptionResponse> availableProviders
) {
}
