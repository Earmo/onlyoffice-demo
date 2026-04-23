package com.earmo.onlyoffice.integration.service.llm;

/**
 * 发送给 provider 的标准消息单元。
 */
public record LlmProviderMessage(
    String role,
    String content
) {
}
