package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 前端模型选择器中展示的 provider 选项。
 */
@Schema(description = "前端模型选择器中展示的 provider 选项。")
public record LlmProviderOptionResponse(
    @Schema(description = "逻辑 provider 名称，对应 llm.providers 下的配置键。", example = "dashscope")
    String provider,
    @Schema(description = "展示给用户的 provider 名称。", example = "通义千问")
    String label,
    @Schema(description = "该 provider 的默认模型。", example = "qwen-plus")
    String defaultModel,
    @Schema(description = "允许用户选择的模型列表。")
    List<String> availableModels,
    @Schema(description = "当前 provider 是否支持向上游转发取消请求。", example = "true")
    boolean supportsUpstreamCancel,
    @Schema(description = "当前 provider 是否启用流式输出。", example = "true")
    boolean streamEnabled
) {
}
