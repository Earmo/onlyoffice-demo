package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentStorageServiceTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("首次预热 demo 文档时创建最小 docx 并写入元数据")
  void shouldCreateDefaultDemoDocument() throws IOException {
    DemoProperties properties = new DemoProperties();
    properties.setStorageRoot(tempDir);

    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    when(metadataService.findDocument("demo")).thenReturn(Optional.empty());
    when(metadataService.createDocument(
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(RequestContext.class),
        nullable(String.class)
    )).thenAnswer(invocation -> entity(
        invocation.getArgument(0, String.class),
        invocation.getArgument(1, String.class),
        invocation.getArgument(4, String.class),
        invocation.getArgument(2, String.class),
        invocation.getArgument(3, String.class)
    ));
    when(metadataService.toStoredDocument(any(DocumentMetadataEntity.class), any(Path.class), any()))
        .thenAnswer(invocation -> {
          DocumentMetadataEntity entity = invocation.getArgument(0, DocumentMetadataEntity.class);
          Path path = invocation.getArgument(1, Path.class);
          return new StoredDocument(
              entity.getDocumentId(),
              entity.getTenantId(),
              entity.getOwnerUserId(),
              entity.getSourceSystem(),
              entity.getExternalDocumentId(),
              entity.getTitle(),
              entity.getStorageKey(),
              entity.getFileType(),
              entity.getDocumentType(),
              entity.getStatus(),
              path,
              invocation.getArgument(2, java.time.Instant.class),
              null,
              null,
              null,
              null
          );
        });

    DocumentStorageService service = new DocumentStorageService(properties, metadataService, RestClient.builder());
    StoredDocument document = service.ensureBootstrapDocument("demo");

    assertEquals("demo.docx", document.title());
    assertEquals("documents/demo.docx", document.storageKey());
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

    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    when(metadataService.createDocument(
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(RequestContext.class),
        nullable(String.class)
    )).thenAnswer(invocation -> {
      String documentId = invocation.getArgument(0, String.class);
      String title = invocation.getArgument(1, String.class);
      String storageKey = invocation.getArgument(4, String.class);
      String fileType = invocation.getArgument(2, String.class);
      String documentType = invocation.getArgument(3, String.class);
      return entity(documentId, title, storageKey, fileType, documentType);
    });
    when(metadataService.toStoredDocument(any(DocumentMetadataEntity.class), any(Path.class), any()))
        .thenAnswer(invocation -> {
          DocumentMetadataEntity entity = invocation.getArgument(0, DocumentMetadataEntity.class);
          Path path = invocation.getArgument(1, Path.class);
          return new StoredDocument(
              entity.getDocumentId(),
              entity.getTenantId(),
              entity.getOwnerUserId(),
              entity.getSourceSystem(),
              entity.getExternalDocumentId(),
              entity.getTitle(),
              entity.getStorageKey(),
              entity.getFileType(),
              entity.getDocumentType(),
              entity.getStatus(),
              path,
              invocation.getArgument(2, java.time.Instant.class),
              null,
              null,
              null,
              null
          );
        });

    DocumentStorageService service = new DocumentStorageService(properties, metadataService, RestClient.builder());
    StoredDocument document = service.storeUploadedDocument("sales-report.xlsx", "xlsx-demo".getBytes());

    assertTrue(document.title().startsWith("sales-report-"));
    assertTrue(document.title().endsWith(".xlsx"));
    assertEquals("xlsx", document.fileType());
    assertEquals("cell", document.documentType());
    assertTrue(document.documentId().startsWith("sales-report-"));
    assertTrue(Files.exists(document.path()));
  }

  private DocumentMetadataEntity entity(
      String documentId,
      String title,
      String storageKey,
      String fileType,
      String documentType
  ) {
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId(documentId);
    entity.setTenantId("native");
    entity.setOwnerUserId("demo-user");
    entity.setSourceSystem("native");
    entity.setTitle(title);
    entity.setStorageKey(storageKey);
    entity.setFileType(fileType);
    entity.setDocumentType(documentType);
    entity.setStatus("draft");
    return entity;
  }
}
