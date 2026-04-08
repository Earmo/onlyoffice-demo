package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ONLYOFFICE callback 的最小请求体。
 *
 * @param status 文档当前状态码，示例里只关心 2 和 6
 * @param url 当文档可持久化时，ONLYOFFICE 提供的最新文件下载地址
 * @param filetype ONLYOFFICE 推断出的文件扩展名，部分保存场景会把 legacy 格式升级为 OOXML
 */
@Schema(description = "ONLYOFFICE callback 的最小请求体。")
public record OnlyofficeCallbackRequest(
    @Schema(description = "ONLYOFFICE 回调状态码。", example = "2")
    Integer status,
    @Schema(description = "ONLYOFFICE 提供的最新文档下载地址。", example = "http://onlyoffice/cache/files/demo.docx")
    String url,
    @Schema(description = "ONLYOFFICE 提供的最新文档扩展名。", example = "docx")
    String filetype
) {
}


