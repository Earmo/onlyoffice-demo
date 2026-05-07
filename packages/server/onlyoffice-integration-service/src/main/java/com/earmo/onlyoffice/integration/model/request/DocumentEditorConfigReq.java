package com.earmo.onlyoffice.integration.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 获取 ONLYOFFICE 编辑器配置的请求体。
 *
 * @param documentId 内部文档 ID。
 * @param readonly   是否以只读模式打开。
 */
@Schema(description = "获取 ONLYOFFICE 编辑器配置的请求体。")
public record DocumentEditorConfigReq(
        @Schema(description = "内部文档 ID。", example = "demo")
        @NotBlank(message = "documentId 不能为空。")
        String documentId,
        @Schema(description = "是否以只读模式打开。", example = "false")
        Boolean readonly
) {
}
