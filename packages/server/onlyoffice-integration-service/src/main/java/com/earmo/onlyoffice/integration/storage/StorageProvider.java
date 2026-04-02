package com.earmo.onlyoffice.integration.storage;

/**
 * 文档内容存储 provider 枚举。
 *
 * <p>当前正式支持 local、minio 和 cos，三者都必须遵守同一套上层编排语义。
 */
public enum StorageProvider {
  LOCAL,
  MINIO,
  COS
}
