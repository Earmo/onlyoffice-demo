package com.earmo.onlyoffice.integration.storage;

import java.util.Arrays;

/**
 * 统一封装一次对象写入请求。
 *
 * <p>把 storage key、内容类型和字节内容收口成独立对象，可以避免 provider 实现直接依赖业务 service 的局部变量。
 */
public record StorageWriteRequest(
    String storageKey,
    String contentType,
    byte[] body
) {

  public StorageWriteRequest {
    body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
  }

  public long size() {
    return body.length;
  }
}
