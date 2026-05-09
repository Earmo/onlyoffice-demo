package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档元数据实体。
 *
 * <p>这个实体同时承担 3 层职责：
 * 1. 作为数据库 `document_metadata` 表的映射对象；
 * 2. 作为 repository 与 service 之间传递的主数据载体；
 * 3. 作为字段命名规范的落点，确保用户字段统一使用 `*_user`，时间字段统一使用 `*_time`。
 */
@Schema(description = "文档元数据实体，用于持久化文档归属、来源、存储定位和编辑状态。")
@Table("document_metadata")
@Getter
@Setter
public class DocumentMetadataEntity {

  /** 文档服务内部生成的稳定主键。 */
  @Schema(description = "文档服务内部生成的稳定主键。", example = "sample")
  @Id
  @Column("document_id")
  private String documentId;

  /** 文档所属租户标识。 */
  @Schema(description = "文档所属租户标识。", example = "native")
  @Column("tenant_id")
  private String tenantId;

  /** 文档所属组织标识。 */
  @Schema(description = "文档所属组织标识。", example = "org-3301")
  @Column("org_id")
  private String orgId;

  /** 文档所属组织名称。 */
  @Schema(description = "文档所属组织名称。", example = "华东区域公司")
  @Column("org_name")
  private String orgName;

  /** 文档 owner 用户标识。 */
  @Schema(description = "文档 owner 用户标识。", example = "starter-user")
  @Column("owner_user")
  private String ownerUser;

  /** 文档来源系统标识。 */
  @Schema(description = "文档来源系统标识。", example = "native")
  @Column("source_system")
  private String sourceSystem;

  /** 上游系统传入的外部文档 ID。 */
  @Schema(description = "上游系统传入的外部文档 ID。", example = "external-1")
  @Column("external_document_id")
  private String externalDocumentId;

  /** 展示给用户的文档标题。 */
  @Schema(description = "展示给用户的文档标题。", example = "sample.docx")
  @Column("title")
  private String title;

  /** 文档在存储系统中的稳定对象键。 */
  @Schema(description = "文档在存储系统中的稳定对象键。", example = "documents/sample.docx")
  @Column("storage_key")
  private String storageKey;

  /** 文件扩展名。 */
  @Schema(description = "文件扩展名。", example = "docx")
  @Column("file_type")
  private String fileType;

  /** ONLYOFFICE 文档类型。 */
  @Schema(description = "ONLYOFFICE 文档类型。", example = "word")
  @Column("document_type")
  private String documentType;

  /** 文档当前主状态。 */
  @Schema(description = "文档当前主状态。", example = "draft")
  @Column("status")
  private String status;

  /** 最近一次 ONLYOFFICE callback 的状态码。 */
  @Schema(description = "最近一次 ONLYOFFICE callback 的状态码。", example = "2")
  @Column("last_callback_status")
  private Integer lastCallbackStatus;

  /** 最近一次保存失败原因。 */
  @Schema(description = "最近一次保存失败原因。", example = "下载失败")
  @Column("last_error_message")
  private String lastErrorMessage;

  /** 文档元数据创建时间。 */
  @Schema(description = "文档元数据创建时间。")
  @Column("created_time")
  private Instant createdTime;

  /** 文档元数据最近更新时间。 */
  @Schema(description = "文档元数据最近更新时间。")
  @Column("updated_time")
  private Instant updatedTime;

  /** 最近一次打开编辑器的时间。 */
  @Schema(description = "最近一次打开编辑器的时间。")
  @Column("last_opened_time")
  private Instant lastOpenedTime;

  /** 最近一次收到 ONLYOFFICE callback 的时间。 */
  @Schema(description = "最近一次收到 ONLYOFFICE callback 的时间。")
  @Column("last_callback_time")
  private Instant lastCallbackTime;

  /** 最近一次成功保存回写时间。 */
  @Schema(description = "最近一次成功保存回写时间。")
  @Column("last_saved_time")
  private Instant lastSavedTime;
}
