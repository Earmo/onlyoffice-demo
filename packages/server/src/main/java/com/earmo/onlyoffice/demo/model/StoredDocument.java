package com.earmo.onlyoffice.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 文档文件与主数据的聚合视图。
 */
@Schema(description = "文档文件与主数据聚合后的内部视图。")
public record StoredDocument(
    @Schema(description = "文档内部主键。", example = "demo")
    String documentId,
    @Schema(description = "文档所属租户。", example = "native")
    String tenantId,
    @Schema(description = "owner 用户 ID。", example = "demo-user")
    String ownerUserId,
    @Schema(description = "来源系统标识。", example = "native")
    String sourceSystem,
    @Schema(description = "外部业务文档 ID。", example = "external-1")
    String externalDocumentId,
    @Schema(description = "展示标题。", example = "demo.docx")
    String title,
    @Schema(description = "存储系统对象键。", example = "documents/demo.docx")
    String storageKey,
    @Schema(description = "文件扩展名。", example = "docx")
    String fileType,
    @Schema(description = "ONLYOFFICE 文档类型。", example = "word")
    String documentType,
    @Schema(description = "当前主状态。", example = "draft")
    String status,
    @Schema(description = "映射到本地或共享挂载目录的真实路径。")
    Path path,
    @Schema(description = "文件最近修改时间。")
    Instant lastModified,
    @Schema(description = "最近一次打开时间。")
    Instant lastOpenedAt,
    @Schema(description = "最近一次成功保存时间。")
    Instant lastSavedAt,
    @Schema(description = "最近一次 ONLYOFFICE callback 状态码。", example = "2")
    Integer lastCallbackStatus,
    @Schema(description = "最近一次保存失败的错误信息。", example = "下载失败")
    String lastErrorMessage
) {
}
