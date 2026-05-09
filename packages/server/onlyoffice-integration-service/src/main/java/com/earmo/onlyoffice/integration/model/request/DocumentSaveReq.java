package com.earmo.onlyoffice.integration.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 触发文档保存的请求体。
 *
 * @param documentId 内部文档 ID。
 */
@Schema(description = "触发文档保存的请求体。")
public record DocumentSaveReq(
        @Schema(description = "内部文档 ID。", example = "demo")
        @NotBlank(message = "documentId 不能为空。")
        String documentId
) {
}
