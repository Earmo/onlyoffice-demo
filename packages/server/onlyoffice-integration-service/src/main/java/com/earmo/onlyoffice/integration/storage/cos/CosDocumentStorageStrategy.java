package com.earmo.onlyoffice.integration.storage.cos;

import com.earmo.onlyoffice.integration.storage.DocumentStorageStrategy;
import com.earmo.onlyoffice.integration.storage.StorageProvider;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 COS provider 实现。
 *
 * <p>实现继续遵守 provider-neutral 语义：
 * bucket 只是对象容器，真正的业务身份仍然完全来自 storageKey。
 * 这样上层 service、controller 和返回模型都不需要感知 COS 专有概念。
 */
@Component
@RequiredArgsConstructor
public class CosDocumentStorageStrategy implements DocumentStorageStrategy {

  private final CosClientFactory cosClientFactory;

  @Override
  public StorageProvider provider() {
    return StorageProvider.COS;
  }

  @Override
  public boolean exists(String storageKey) throws IOException {
    try {
      return cosClientFactory.client().doesObjectExist(cosClientFactory.bucket(), storageKey);
    } catch (CosClientException ex) {
      throw toIoException("检查 COS 对象是否存在失败：" + storageKey, ex);
    }
  }

  @Override
  public StoredObjectResource read(String storageKey) throws IOException {
    COSClient client = cosClientFactory.client();
    String bucket = cosClientFactory.bucket();
    try {
      if (!client.doesObjectExist(bucket, storageKey)) {
        throw new IOException("存储对象不存在：" + storageKey);
      }

      ObjectMetadata metadata = client.getObjectMetadata(bucket, storageKey);
      COSObject object = client.getObject(new GetObjectRequest(bucket, storageKey));
      try (InputStream inputStream = object.getObjectContent()) {
        byte[] body = inputStream.readAllBytes();
        return new StoredObjectResource(
            storageKey,
            metadata.getContentType(),
            body,
            metadata.getContentLength(),
            toInstant(metadata.getLastModified()),
            null
        );
      }
    } catch (IOException ex) {
      throw ex;
    } catch (CosClientException ex) {
      throw toIoException("读取 COS 对象失败：" + storageKey, ex);
    }
  }

  @Override
  public StoredObjectResource writeNew(StorageWriteRequest request) throws IOException {
    return putObject(request);
  }

  @Override
  public StoredObjectResource overwrite(StorageWriteRequest request) throws IOException {
    return putObject(request);
  }

  @Override
  public void delete(String storageKey) throws IOException {
    try {
      COSClient client = cosClientFactory.client();
      String bucket = cosClientFactory.bucket();
      if (!client.doesObjectExist(bucket, storageKey)) {
        return;
      }
      client.deleteObject(bucket, storageKey);
    } catch (CosClientException ex) {
      throw toIoException("删除 COS 对象失败：" + storageKey, ex);
    }
  }

  /**
   * 写入步骤与 MinIO 保持一致：
   * 1. 先按 storageKey 写入对象；
   * 2. 再立即回读，拿到统一的元数据投影；
   * 3. 上层只消费 `StoredObjectResource`，不直接碰 SDK 返回类型。
   */
  private StoredObjectResource putObject(StorageWriteRequest request) throws IOException {
    try {
      byte[] body = request.body();
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(body.length);
      metadata.setContentType(request.contentType());
      cosClientFactory.client().putObject(
          cosClientFactory.bucket(),
          request.storageKey(),
          new ByteArrayInputStream(body),
          metadata
      );
      return read(request.storageKey());
    } catch (IOException ex) {
      throw ex;
    } catch (CosClientException ex) {
      throw toIoException("写入 COS 对象失败：" + request.storageKey(), ex);
    }
  }

  private Instant toInstant(Date date) {
    return date == null ? Instant.now() : date.toInstant();
  }

  private IOException toIoException(String message, Exception ex) {
    return new IOException(message, ex);
  }
}
