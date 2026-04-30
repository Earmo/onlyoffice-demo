package com.earmo.onlyoffice.integration.storage.minio;

import com.earmo.onlyoffice.integration.storage.DocumentStorageStrategy;
import com.earmo.onlyoffice.integration.storage.StorageProvider;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * MinIO provider 实现。
 *
 * <p>实现保持 provider-neutral 语义：bucket 只是存储承载体，业务身份仍全部来自 `storageKey`。
 */
@Component
@RequiredArgsConstructor
public class MinioDocumentStorageStrategy implements DocumentStorageStrategy {

    private final MinioClientFactory minioClientFactory;

    @Override
    public StorageProvider provider() {
        return StorageProvider.MINIO;
    }

    @Override
    public boolean exists(String storageKey) throws IOException {
        try {
            stat(storageKey);
            return true;
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equalsIgnoreCase(ex.errorResponse().code())
                    || "NoSuchBucket".equalsIgnoreCase(ex.errorResponse().code())) {
                return false;
            }
            throw toIoException("检查 MinIO 对象是否存在失败：" + storageKey, ex);
        } catch (Exception ex) {
            throw toIoException("检查 MinIO 对象是否存在失败：" + storageKey, ex);
        }
    }

    @Override
    public StoredObjectResource read(String storageKey) throws IOException {
        try {
            StatObjectResponse stat = stat(storageKey);
            MinioClient client = minioClientFactory.client();
            try (InputStream inputStream = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioClientFactory.bucket())
                            .object(storageKey)
                            .build()
            )) {
                byte[] body = inputStream.readAllBytes();
                return new StoredObjectResource(
                        storageKey,
                        stat.contentType(),
                        body,
                        stat.size(),
                        stat.lastModified().toInstant(),
                        null
                );
            }
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equalsIgnoreCase(ex.errorResponse().code())
                    || "NoSuchBucket".equalsIgnoreCase(ex.errorResponse().code())) {
                throw new IOException("存储对象不存在：" + storageKey, ex);
            }
            throw toIoException("读取 MinIO 对象失败：" + storageKey, ex);
        } catch (Exception ex) {
            throw toIoException("读取 MinIO 对象失败：" + storageKey, ex);
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
            minioClientFactory.client().removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioClientFactory.bucket())
                            .object(storageKey)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equalsIgnoreCase(ex.errorResponse().code())
                    || "NoSuchBucket".equalsIgnoreCase(ex.errorResponse().code())) {
                return;
            }
            throw toIoException("删除 MinIO 对象失败：" + storageKey, ex);
        } catch (Exception ex) {
            throw toIoException("删除 MinIO 对象失败：" + storageKey, ex);
        }
    }

    private StoredObjectResource putObject(StorageWriteRequest request) throws IOException {
        try {
            minioClientFactory.ensureBucketExists();
            byte[] body = request.body();
            minioClientFactory.client().putObject(
                    PutObjectArgs.builder()
                            .bucket(minioClientFactory.bucket())
                            .object(request.storageKey())
                            .contentType(request.contentType())
                            .stream(new ByteArrayInputStream(body), body.length, -1)
                            .build()
            );
            return read(request.storageKey());
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw toIoException("写入 MinIO 对象失败：" + request.storageKey(), ex);
        }
    }

    private StatObjectResponse stat(String storageKey) throws Exception {
        return minioClientFactory.client().statObject(
                StatObjectArgs.builder()
                        .bucket(minioClientFactory.bucket())
                        .object(storageKey)
                        .build()
        );
    }

    private IOException toIoException(String message, Exception ex) {
        return new IOException(message, ex);
    }
}
