package com.earmo.onlyoffice.integration.service.llm;

/**
 * provider 返回的 token 使用量。
 */
public record LlmProviderUsage(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens
) {
}
