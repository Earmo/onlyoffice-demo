package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStorageServiceTest {

  @TempDir
  java.nio.file.Path tempDir;

  @Test
  @DisplayName("首次访问 demo 文档时自动生成一个最小 docx")
  void shouldCreateDefaultDemoDocument() throws IOException {
    DemoProperties properties = new DemoProperties();
    properties.setStorageRoot(tempDir);

    DocumentStorageService service = new DocumentStorageService(properties, RestClient.builder());
    StoredDocument document = service.getOrCreateDocument("demo");

    assertEquals("demo.docx", document.title());
    assertTrue(Files.exists(document.path()));
    byte[] bytes = Files.readAllBytes(document.path());
    assertTrue(bytes.length > 4);
    assertEquals("504b0304", HexFormat.of().formatHex(bytes, 0, 4));
  }

  @Test
  @DisplayName("上传文档后保留原始扩展名并生成新的 documentId")
  void shouldStoreUploadedDocumentWithOriginalExtension() throws IOException {
    DemoProperties properties = new DemoProperties();
    properties.setStorageRoot(tempDir);

    DocumentStorageService service = new DocumentStorageService(properties, RestClient.builder());
    StoredDocument document = service.storeUploadedDocument("sales-report.xlsx", "xlsx-demo".getBytes());

    assertTrue(document.title().startsWith("sales-report-"));
    assertTrue(document.title().endsWith(".xlsx"));
    assertEquals("xlsx", document.fileType());
    assertEquals("cell", document.documentType());
    assertTrue(document.documentId().startsWith("sales-report-"));
    assertTrue(Files.exists(document.path()));
  }
}
