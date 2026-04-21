package com.earmo.onlyoffice.integration.service.llm;

public record LlmProviderUsage(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens
) {
}
