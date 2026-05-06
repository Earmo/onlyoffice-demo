package com.earmo.onlyoffice.integration.storage.impl.minio;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一创建和复用 MinIO Java 客户端。
 */
@Component
@RequiredArgsConstructor
public class MinioClientFactory {

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final MinioClient client = buildClient();

    public MinioClient client() {
        return getClient();
    }

    public String bucket() {
        return onlyofficeIntegrationProperties.getStorage().getMinio().getBucket();
    }

    public void ensureBucketExists() throws Exception {
        String bucket = bucket();
        MinioClient client = client();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private MinioClient buildClient() {
        OnlyofficeIntegrationProperties.MinioStorageProperties properties =
                onlyofficeIntegrationProperties.getStorage().getMinio();
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
