package com.earmo.onlyoffice.integration.model.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 查询 AI 会话列表的请求体。
 *
 * @param documentId 内部文档 ID。
 */
@Schema(description = "查询 AI 会话列表的请求体。")
public record LlmSessionListReq(
        @Schema(description = "内部文档 ID。", example = "demo")
        @NotBlank(message = "documentId 不能为空。")
        String documentId
) {
}
