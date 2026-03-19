package com.earmo.onlyoffice.demo.model;

import java.time.Instant;

/**
 * 返回给前端的文档概要。
 */
public record DocumentSummaryResponse(
    String documentId,
    String title,
    String fileType,
    String documentType,
    String status,
    String tenantId,
    String ownerUserId,
    String sourceSystem,
    String externalDocumentId,
    Instant lastOpenedAt,
    Instant lastSavedAt
) {
}
