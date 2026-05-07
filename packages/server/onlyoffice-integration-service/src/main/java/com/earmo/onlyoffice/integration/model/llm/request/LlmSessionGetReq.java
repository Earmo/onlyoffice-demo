package com.earmo.onlyoffice.integration.model.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 查询 AI 会话详情的请求体。
 *
 * @param documentId 内部文档 ID。
 * @param sessionId  AI 会话 ID。
 */
@Schema(description = "查询 AI 会话详情的请求体。")
public record LlmSessionGetReq(
        @Schema(description = "内部文档 ID。", example = "demo")
        @NotBlank(message = "documentId 不能为空。")
        String documentId,
        @Schema(description = "AI 会话 ID。", example = "session-1")
        @NotBlank(message = "sessionId 不能为空。")
        String sessionId
) {
}
