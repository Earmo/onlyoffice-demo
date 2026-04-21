package com.earmo.onlyoffice.integration.model.llm;

public record LlmUsageResponse(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens
) {
}
