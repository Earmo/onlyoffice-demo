package com.earmo.onlyoffice.demo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 文档主数据实体。
 */
@Entity
@Table(
    name = "document_metadata",
    indexes = {
        @Index(name = "idx_document_metadata_tenant_updated", columnList = "tenant_id, updated_at"),
        @Index(name = "idx_document_metadata_source_external", columnList = "source_system, external_document_id")
    }
)
public class DocumentMetadataEntity {

  @Id
  @Column(name = "document_id", nullable = false, updatable = false, length = 128)
  private String documentId;

  @Column(name = "tenant_id", nullable = false, length = 128)
  private String tenantId;

  @Column(name = "owner_user_id", nullable = false, length = 128)
  private String ownerUserId;

  @Column(name = "source_system", nullable = false, length = 128)
  private String sourceSystem;

  @Column(name = "external_document_id", length = 256)
  private String externalDocumentId;

  @Column(name = "title", nullable = false, length = 512)
  private String title;

  @Column(name = "storage_key", nullable = false, length = 512)
  private String storageKey;

  @Column(name = "file_type", nullable = false, length = 32)
  private String fileType;

  @Column(name = "document_type", nullable = false, length = 32)
  private String documentType;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "last_callback_status")
  private Integer lastCallbackStatus;

  @Column(name = "last_error_message", length = 1024)
  private String lastErrorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_opened_at")
  private Instant lastOpenedAt;

  @Column(name = "last_callback_at")
  private Instant lastCallbackAt;

  @Column(name = "last_saved_at")
  private Instant lastSavedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

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
