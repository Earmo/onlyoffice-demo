package com.earmo.onlyoffice.integration.storage;

/**
 * 文档内容存储 provider 枚举。
 *
 * <p>Phase 2 先正式支持 local 和 minio，两者都必须遵守同一套上层编排语义。
 */
public enum StorageProvider {
  LOCAL,
  MINIO
}
