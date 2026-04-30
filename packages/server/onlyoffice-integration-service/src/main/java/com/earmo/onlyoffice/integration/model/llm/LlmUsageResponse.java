package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 模型调用的 token 用量统计。
 */
@Schema(description = "模型调用 token 用量统计。")
public record LlmUsageResponse(
        @Schema(description = "输入 prompt 消耗的 token 数。", example = "1200")
        Integer promptTokens,
        @Schema(description = "模型输出消耗的 token 数。", example = "480")
        Integer completionTokens,
        @Schema(description = "输入与输出合计 token 数。", example = "1680")
        Integer totalTokens
) {
}
