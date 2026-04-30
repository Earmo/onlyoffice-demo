package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.model.StoredDocument;

import java.time.Instant;

/**
 * 统一生成 ONLYOFFICE 文档 key。
 *
 * <p>这里刻意不直接使用对象存储的 lastModified：
 * 1. 编辑会话进行中 callback 回写会推进文件修改时间；
 * 2. 如果 key 也跟着变化，当前仍在编辑中的页面会被 ONLYOFFICE 视为旧版本；
 * 3. 因此编辑态优先绑定“本轮编辑会话批次时间”，只在下一轮重新进入编辑时再切 key。
 */
final class OnlyofficeDocumentKeyResolver {

    /**
     * 禁止实例化工具类。
     */
    private OnlyofficeDocumentKeyResolver() {
    }

    /**
     * 生成 ONLYOFFICE 文档 key。
     *
     * @param storedDocument 当前文档快照。
     * @return 文档 ID 和版本时间组合成的 key。
     */
    static String resolveDocumentKey(StoredDocument storedDocument) {
        return storedDocument.documentId() + "-" + resolveVersionInstant(storedDocument).toEpochMilli();
    }

    /**
     * 解析用于 ONLYOFFICE key 的版本时间。
     *
     * @param storedDocument 当前文档快照。
     * @return key 使用的版本时间。
     */
    private static Instant resolveVersionInstant(StoredDocument storedDocument) {
        if ("editing".equals(storedDocument.status()) && storedDocument.lastOpenedTime() != null) {
            return storedDocument.lastOpenedTime();
        }
        if (storedDocument.lastSavedTime() != null) {
            return storedDocument.lastSavedTime();
        }
        if (storedDocument.lastModified() != null) {
            return storedDocument.lastModified();
        }
        if (storedDocument.lastOpenedTime() != null) {
            return storedDocument.lastOpenedTime();
        }
        return Instant.EPOCH;
    }
}
