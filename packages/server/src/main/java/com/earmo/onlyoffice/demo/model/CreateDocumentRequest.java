package com.earmo.onlyoffice.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 显式创建文档时的请求体。
 */
@Schema(description = "显式创建文档时的请求体。")
public record CreateDocumentRequest(
    @Schema(description = "调用方希望指定的内部文档 ID；为空时由服务端自动生成。", example = "doc-1")
    String documentId,
    @Schema(description = "文档标题，当前显式创建接口只接受 docx。", example = "alpha.docx")
    String title,
    @Schema(description = "外部业务系统中的文档 ID。", example = "external-1")
    String externalDocumentId
) {
}
