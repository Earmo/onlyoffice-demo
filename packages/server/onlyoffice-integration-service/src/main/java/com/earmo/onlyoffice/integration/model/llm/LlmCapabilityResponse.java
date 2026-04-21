package com.earmo.onlyoffice.integration.model.llm;

public record LlmCapabilityResponse(
    String documentId,
    boolean llmAvailable,
    String disabledReason,
    String provider,
    String model,
    boolean supportsUpstreamCancel
) {
}
