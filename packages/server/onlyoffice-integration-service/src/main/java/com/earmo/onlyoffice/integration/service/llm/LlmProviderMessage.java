package com.earmo.onlyoffice.integration.service.llm;

public record LlmProviderMessage(
    String role,
    String content
) {
}
