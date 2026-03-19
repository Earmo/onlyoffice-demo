package com.earmo.onlyoffice.demo.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 文档文件与主数据的聚合视图。
 */
public record StoredDocument(
    String documentId,
    String tenantId,
    String ownerUserId,
    String sourceSystem,
    String externalDocumentId,
    String title,
    String storageKey,
    String fileType,
    String documentType,
    String status,
    Path path,
    Instant lastModified,
    Instant lastOpenedAt,
    Instant lastSavedAt,
    Integer lastCallbackStatus,
    String lastErrorMessage
) {
}
