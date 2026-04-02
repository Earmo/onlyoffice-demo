package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ONLYOFFICE callback 的最小请求体。
 *
 * @param status 文档当前状态码，示例里只关心 2 和 6
 * @param url 当文档可持久化时，ONLYOFFICE 提供的最新文件下载地址
 */
@Schema(description = "ONLYOFFICE callback 的最小请求体。")
public record OnlyofficeCallbackRequest(
    @Schema(description = "ONLYOFFICE 回调状态码。", example = "2")
    Integer status,
    @Schema(description = "ONLYOFFICE 提供的最新文档下载地址。", example = "http://onlyoffice/cache/files/demo.docx")
    String url
) {
}


