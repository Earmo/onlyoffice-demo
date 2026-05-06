package com.earmo.onlyoffice.integration.model.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建 AI 对话会话的请求体。
 */
@Schema(description = "创建 AI 对话会话的请求体。")
public record CreateLlmSessionRequest(
        @Schema(description = "会话所属的内部文档 ID。", example = "sample")
        @NotBlank
        String documentId,
        @Schema(description = "可选会话标题；为空时后端会生成默认标题。", example = "合同风险审阅")
        String title
) {
}
