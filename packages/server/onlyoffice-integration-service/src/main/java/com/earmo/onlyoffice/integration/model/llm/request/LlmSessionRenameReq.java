package com.earmo.onlyoffice.integration.model.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 重命名 AI 会话的请求体。
 *
 * @param documentId 内部文档 ID。
 * @param sessionId  AI 会话 ID。
 * @param title      新会话标题。
 */
@Schema(description = "重命名 AI 会话的请求体。")
public record LlmSessionRenameReq(
        @Schema(description = "内部文档 ID。", example = "demo")
        @NotBlank(message = "documentId 不能为空。")
        String documentId,
        @Schema(description = "AI 会话 ID。", example = "session-1")
        @NotBlank(message = "sessionId 不能为空。")
        String sessionId,
        @Schema(description = "新会话标题。", example = "合同风险审阅")
        @NotBlank(message = "title 不能为空。")
        String title
) {
}
