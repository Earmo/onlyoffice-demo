package com.earmo.onlyoffice.demo.model;

/**
 * 返回给前端的文档概要。
 *
 * @param documentId 当前文档 ID
 * @param title 当前文档标题
 * @param fileType 当前文档扩展名
 * @param documentType ONLYOFFICE 文档大类
 */
public record DocumentSummaryResponse(
    String documentId,
    String title,
    String fileType,
    String documentType
) {
}

