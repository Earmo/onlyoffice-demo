package com.earmo.onlyoffice.integration.model.llm.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 当前文档可用的 AI 能力描述。
 */
@Schema(description = "当前文档可用的 AI 能力描述。")
public record LlmCapabilityResponse(
        @Schema(description = "当前能力判断对应的内部文档 ID。", example = "sample")
        String documentId,
        @Schema(description = "AI 能力是否可用。", example = "true")
        boolean llmAvailable,
        @Schema(description = "不可用时的稳定错误码。", example = "LLM_UNAVAILABLE")
        String disabledReason,
        @Schema(description = "当前默认逻辑 provider。", example = "dashscope")
        String provider,
        @Schema(description = "当前默认模型。", example = "qwen-plus")
        String model,
        @Schema(description = "默认 provider 是否支持上游取消。", example = "true")
        boolean supportsUpstreamCancel,
        @Schema(description = "默认 provider 是否启用流式模式。", example = "true")
        boolean streamMode,
        @Schema(description = "配置解析后的默认 provider。", example = "dashscope")
        String defaultProvider,
        @Schema(description = "配置解析后的默认模型。", example = "qwen-plus")
        String defaultModel,
        @Schema(description = "当前用户可选的 provider 列表。")
        List<LlmProviderOptionResponse> availableProviders
) {
}
