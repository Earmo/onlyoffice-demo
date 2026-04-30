package com.earmo.onlyoffice.integration.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 导入网络文档时的请求体。
 *
 * @param sourceUrl 可公开访问的文档 URL
 */
@Schema(description = "导入网络文档时的请求体。")
public record DocumentImportRequest(
        @NotBlank
        @Schema(description = "可公开访问的远程文档地址，仅支持 http/https。", example = "https://example.com/demo.docx")
        String sourceUrl
) {
}



