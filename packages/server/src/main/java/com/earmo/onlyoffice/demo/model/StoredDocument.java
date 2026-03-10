package com.earmo.onlyoffice.demo.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 本地文档的统一描述对象。
 *
 * <p>这里把文件名、类型、路径和最后修改时间打包起来，便于后续生成
 * ONLYOFFICE 配置时直接复用。
 */
public record StoredDocument(
    String documentId,
    String title,
    String fileType,
    String documentType,
    Path path,
    Instant lastModified
) {
}
