package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * assistant 消息的一次具体生成版本。
 */
@Schema(description = "assistant 消息的一次具体生成版本。")
public record LlmMessageVariantResponse(
    @Schema(description = "variant 主键。", example = "variant-1")
    String variantId,
    @Schema(description = "同一 assistant message 下的版本序号。", example = "0")
    int variantIndex,
    @Schema(description = "该版本的 assistant 正文。")
    String assistantText,
    @Schema(description = "该版本状态。", example = "completed")
    String status,
    @Schema(description = "失败时的稳定错误码。", example = "LLM_PROVIDER_ERROR")
    String errorCode,
    @Schema(description = "模型返回的结束原因。", example = "stop")
    String finishReason,
    @Schema(description = "该版本模型调用的 token 用量。")
    LlmUsageResponse usage,
    @Schema(description = "白名单过滤后的 provider 响应元数据。")
    Map<String, Object> providerResponseMeta,
    @Schema(description = "variant 创建时间。")
    Instant createdTime
) {
}
