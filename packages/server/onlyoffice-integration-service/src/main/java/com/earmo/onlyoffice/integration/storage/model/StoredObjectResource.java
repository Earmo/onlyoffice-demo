package com.earmo.onlyoffice.integration.storage.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

/**
 * provider 读取对象后的统一结果。
 *
 * <p>MinIO 和 local 都通过这个只读视图把内容、修改时间和可选本地路径返回给上层编排服务。
 */
public record StoredObjectResource(
        String storageKey,
        String contentType,
        byte[] body,
        long size,
        Instant lastModified,
        Path localPath
) {

    public StoredObjectResource {
        body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
    }
}
