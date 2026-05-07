package com.earmo.onlyoffice.integration.model.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 取消 AI 请求的请求体。
 *
 * @param documentId 内部文档 ID。
 * @param requestId  AI 请求 ID。
 */
@Schema(description = "取消 AI 请求的请求体。")
public record LlmRequestCancelReq(
        @Schema(description = "内部文档 ID。", example = "demo")
        @NotBlank(message = "documentId 不能为空。")
        String documentId,
        @Schema(description = "AI 请求 ID。", example = "request-1")
        @NotBlank(message = "requestId 不能为空。")
        String requestId
) {
}
