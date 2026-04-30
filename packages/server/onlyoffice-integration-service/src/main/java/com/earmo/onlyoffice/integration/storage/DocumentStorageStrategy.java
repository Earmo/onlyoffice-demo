package com.earmo.onlyoffice.integration.storage;

import java.io.IOException;

/**
 * 统一抽象文档对象存储能力。
 *
 * <p>业务层只关心“读、写、覆盖、删除、存在性”，不直接关心 provider 背后是本地目录还是对象存储。
 */
public interface DocumentStorageStrategy {

    StorageProvider provider();

    boolean exists(String storageKey) throws IOException;

    StoredObjectResource read(String storageKey) throws IOException;

    StoredObjectResource writeNew(StorageWriteRequest request) throws IOException;

    StoredObjectResource overwrite(StorageWriteRequest request) throws IOException;

    void delete(String storageKey) throws IOException;
}
