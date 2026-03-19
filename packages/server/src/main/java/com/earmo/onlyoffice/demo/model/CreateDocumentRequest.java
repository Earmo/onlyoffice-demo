package com.earmo.onlyoffice.demo.model;

/**
 * 显式创建文档时的请求体。
 */
public record CreateDocumentRequest(
    String documentId,
    String title,
    String externalDocumentId
) {
}
