package com.earmo.onlyoffice.integration.storage.minio;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MinioDocumentStorageStrategyTest {

  @Container
  static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-02-07T23-21-09Z")
      .withEnv("MINIO_ROOT_USER", "onlyoffice")
      .withEnv("MINIO_ROOT_PASSWORD", "onlyoffice123")
      .withCommand("server", "/data", "--console-address", ":9001")
      .withExposedPorts(9000, 9001);

  @Test
  @DisplayName("MinIO provider 可以写入、读取、覆盖和删除文档对象")
  void shouldWriteReadOverwriteAndDeleteObject() throws IOException {
    MinioDocumentStorageStrategy strategy = strategy();
    String storageKey = "tenant-a/native/doc-1.docx";

    strategy.writeNew(new StorageWriteRequest(storageKey, "application/octet-stream", "v1".getBytes()));
    StoredObjectResource firstRead = strategy.read(storageKey);
    assertArrayEquals("v1".getBytes(), firstRead.body());
    assertTrue(strategy.exists(storageKey));

    strategy.overwrite(new StorageWriteRequest(storageKey, "application/octet-stream", "v2".getBytes()));
    StoredObjectResource secondRead = strategy.read(storageKey);
    assertArrayEquals("v2".getBytes(), secondRead.body());

    strategy.delete(storageKey);
    assertFalse(strategy.exists(storageKey));
  }

  private MinioDocumentStorageStrategy strategy() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.getStorage().getMinio().setEndpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    properties.getStorage().getMinio().setBucket("onlyoffice-documents");
    properties.getStorage().getMinio().setAccessKey("onlyoffice");
    properties.getStorage().getMinio().setSecretKey("onlyoffice123");
    return new MinioDocumentStorageStrategy(new MinioClientFactory(properties));
  }
}
