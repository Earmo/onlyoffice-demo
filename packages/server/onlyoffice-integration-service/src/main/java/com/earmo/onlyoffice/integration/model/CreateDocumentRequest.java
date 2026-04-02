package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 显式创建文档时的请求体。
 */
@Schema(description = "显式创建文档时的请求体。")
public record CreateDocumentRequest(
    @Schema(
        description = "兼容旧调用方保留的字段。Phase 11 起服务端会忽略该值，并统一生成内部 ULID documentId。",
        example = "legacy-doc-id"
    )
    String documentId,
    @Schema(description = "文档标题，当前显式创建接口只接受 docx。", example = "alpha.docx")
    String title,
    @Schema(description = "外部业务系统中的文档 ID。", example = "external-1")
    String externalDocumentId
) {
}


