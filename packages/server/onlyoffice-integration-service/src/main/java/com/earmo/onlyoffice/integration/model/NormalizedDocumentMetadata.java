package com.earmo.onlyoffice.integration.model;

/**
 * 根据实际文件内容归一化后的文档元数据。
 *
 * <p>主要用于处理 ONLYOFFICE 回写后，文件内容格式已经升级，
 * 但数据库里 `title/fileType/documentType` 仍停留在旧值的场景。
 */
public record NormalizedDocumentMetadata(
        String title,
        String fileType,
        String documentType
) {
}
