package com.earmo.onlyoffice.demo.persistence;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 文档元数据实体。
 *
 * <p>这个实体既承载数据库持久化映射，也作为接口层和服务层之间的主数据载体。
 * 因为后续会有微服务接入、对象存储策略切换、编辑状态回写等能力，
 * 所以这里把“谁的文档、从哪来、存在哪里、当前状态如何”集中在一个实体里维护。
 */
@Schema(description = "文档元数据实体，用于持久化文档归属、来源、存储定位和编辑状态。")
@Table("document_metadata")
public class DocumentMetadataEntity {

  @Schema(description = "文档服务内部生成的稳定主键。", example = "demo")
  @Id
  @Column("document_id")
  private String documentId;

  @Schema(description = "文档所属租户标识。", example = "native")
  @Column("tenant_id")
  private String tenantId;

  @Schema(description = "文档 owner 用户标识。", example = "demo-user")
  @Column("owner_user_id")
  private String ownerUserId;

  @Schema(description = "文档来源系统标识。", example = "native")
  @Column("source_system")
  private String sourceSystem;

  @Schema(description = "上游系统传入的外部文档 ID。", example = "external-1")
  @Column("external_document_id")
  private String externalDocumentId;

  @Schema(description = "展示给用户的文档标题。", example = "demo.docx")
  @Column("title")
  private String title;

  @Schema(description = "文档在存储系统中的稳定对象键。", example = "documents/demo.docx")
  @Column("storage_key")
  private String storageKey;

  @Schema(description = "文件扩展名。", example = "docx")
  @Column("file_type")
  private String fileType;

  @Schema(description = "ONLYOFFICE 文档类型。", example = "word")
  @Column("document_type")
  private String documentType;

  @Schema(description = "文档当前主状态。", example = "draft")
  @Column("status")
  private String status;

  @Schema(description = "最近一次 ONLYOFFICE callback 的状态码。", example = "2")
  @Column("last_callback_status")
  private Integer lastCallbackStatus;

  @Schema(description = "最近一次保存失败原因。", example = "下载失败")
  @Column("last_error_message")
  private String lastErrorMessage;

  @Schema(description = "文档元数据创建时间。")
  @Column("created_at")
  private Instant createdAt;

  @Schema(description = "文档元数据最近更新时间。")
  @Column("updated_at")
  private Instant updatedAt;

  @Schema(description = "最近一次打开编辑器的时间。")
  @Column("last_opened_at")
  private Instant lastOpenedAt;

  @Schema(description = "最近一次收到 ONLYOFFICE callback 的时间。")
  @Column("last_callback_at")
  private Instant lastCallbackAt;

  @Schema(description = "最近一次成功保存回写时间。")
  @Column("last_saved_at")
  private Instant lastSavedAt;

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public void setSourceSystem(String sourceSystem) {
    this.sourceSystem = sourceSystem;
  }

  public String getExternalDocumentId() {
    return externalDocumentId;
  }

  public void setExternalDocumentId(String externalDocumentId) {
    this.externalDocumentId = externalDocumentId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getLastCallbackStatus() {
    return lastCallbackStatus;
  }

  public void setLastCallbackStatus(Integer lastCallbackStatus) {
    this.lastCallbackStatus = lastCallbackStatus;
  }

  public String getLastErrorMessage() {
    return lastErrorMessage;
  }

  public void setLastErrorMessage(String lastErrorMessage) {
    this.lastErrorMessage = lastErrorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getLastOpenedAt() {
    return lastOpenedAt;
  }

  public void setLastOpenedAt(Instant lastOpenedAt) {
    this.lastOpenedAt = lastOpenedAt;
  }

  public Instant getLastCallbackAt() {
    return lastCallbackAt;
  }

  public void setLastCallbackAt(Instant lastCallbackAt) {
    this.lastCallbackAt = lastCallbackAt;
  }

  public Instant getLastSavedAt() {
    return lastSavedAt;
  }

  public void setLastSavedAt(Instant lastSavedAt) {
    this.lastSavedAt = lastSavedAt;
  }
}
