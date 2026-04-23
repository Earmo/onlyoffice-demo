package com.earmo.onlyoffice.integration.service.llm;

import java.util.List;

/**
 * 发送给 provider 的运行时请求。
 *
 * <p>这是领域层和 provider 适配层之间的内部契约，
 * 包含目标 provider、鉴权、模型、超时和最终消息窗口。
 */
public record LlmRuntimeRequest(
    String providerName,
    String baseUrl,
    String apiKey,
    String model,
    long timeoutMillis,
    List<LlmProviderMessage> messages
) {
}
