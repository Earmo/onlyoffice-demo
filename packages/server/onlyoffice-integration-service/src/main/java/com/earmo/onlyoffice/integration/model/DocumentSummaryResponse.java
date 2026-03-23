package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 返回给前端的文档概要。
 */
@Schema(description = "返回给前端或上游系统的文档概要信息。")
public record DocumentSummaryResponse(
    @Schema(description = "文档内部主键。", example = "sample")
    String documentId,
    @Schema(description = "文档标题。", example = "sample.docx")
    String title,
    @Schema(description = "文件扩展名。", example = "docx")
    String fileType,
    @Schema(description = "ONLYOFFICE 文档类型。", example = "word")
    String documentType,
    @Schema(description = "文档主状态。", example = "draft")
    String status,
    @Schema(description = "所属租户 ID。", example = "native")
    String tenantId,
    @Schema(description = "owner 用户标识。", example = "starter-user")
    String ownerUser,
    @Schema(description = "来源系统标识。", example = "native")
    String sourceSystem,
    @Schema(description = "外部业务系统文档 ID。", example = "external-1")
    String externalDocumentId,
    @Schema(description = "最近一次打开时间。")
    Instant lastOpenedTime,
    @Schema(description = "最近一次成功保存时间。")
    Instant lastSavedTime
) {
}
