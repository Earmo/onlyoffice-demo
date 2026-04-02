package com.earmo.onlyoffice.integration.storage.local;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDocumentStorageStrategyTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("local provider 可以写入并重新读取文档对象")
  void shouldWriteAndReadDocument() throws IOException {
    LocalDocumentStorageStrategy strategy = strategy();
    StorageWriteRequest request = new StorageWriteRequest(
        "tenant-a/native/doc-1.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "demo-body".getBytes()
    );

    strategy.writeNew(request);
    StoredObjectResource resource = strategy.read(request.storageKey());

    assertTrue(strategy.exists(request.storageKey()));
    assertArrayEquals("demo-body".getBytes(), resource.body());
    assertNotNull(resource.localPath());
    assertTrue(resource.localPath().toString().replace("\\", "/").endsWith("tenant-a/native/doc-1.docx"));
  }

  @Test
  @DisplayName("local provider 会阻止非法 storage key 逃逸出根目录")
  void shouldRejectIllegalStorageKey() {
    LocalDocumentStorageStrategy strategy = strategy();
    StorageWriteRequest request = new StorageWriteRequest("../escape.docx", "application/octet-stream", "x".getBytes());

    assertThrows(IllegalArgumentException.class, () -> strategy.writeNew(request));
  }

  @Test
  @DisplayName("删除后对象不存在")
  void shouldDeleteObject() throws IOException {
    LocalDocumentStorageStrategy strategy = strategy();
    StorageWriteRequest request = new StorageWriteRequest("tenant-a/native/doc-2.docx", "application/octet-stream", "x".getBytes());

    strategy.writeNew(request);
    strategy.delete(request.storageKey());

    assertFalse(strategy.exists(request.storageKey()));
  }

  private LocalDocumentStorageStrategy strategy() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.getStorage().getLocal().setRoot(tempDir);
    return new LocalDocumentStorageStrategy(properties);
  }
}
