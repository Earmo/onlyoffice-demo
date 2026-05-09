package com.earmo.onlyoffice.integration.service.llm;

import java.util.Map;

/**
 * provider 输出的标准化流式分片。
 *
 * <p>无论上游协议如何，都会被折叠成统一结构：
 * `delta` 负责增量文本，`providerRequestId` 用于取消，`usage` 和 `finishReason`
 * 用于终态回写，`providerResponseMeta` 用于白名单元数据透传。
 */
public record SpringAiProviderChunk(
        String delta,
        String providerRequestId,
        LlmProviderUsage usage,
        String finishReason,
        Map<String, Object> providerResponseMeta
) {
}
