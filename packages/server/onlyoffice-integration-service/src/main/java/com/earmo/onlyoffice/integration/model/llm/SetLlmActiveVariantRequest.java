package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 切换 assistant 消息当前展示版本的请求体。
 */
@Schema(description = "切换 assistant 消息当前展示版本的请求体。")
public record SetLlmActiveVariantRequest(
    @Schema(description = "会话所属内部文档 ID。", example = "sample")
    @NotBlank
    @Size(max = 256)
    String documentId,
    @Schema(description = "AI 会话 ID。", example = "session-1")
    @NotBlank
    @Size(max = 256)
    String sessionId,
    @Schema(description = "目标 variant 主键；可与 variantIndex 二选一。", example = "variant-1")
    @Size(max = 128)
    String variantId,
    @Schema(description = "目标 variant 序号；当 variantId 为空时用于定位版本。", example = "1")
    Integer variantIndex
) {
}
