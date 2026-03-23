package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.storage.StorageKeyFactory;
import com.earmo.onlyoffice.integration.storage.StorageProviderResolver;
import com.earmo.onlyoffice.integration.storage.local.LocalDocumentStorageStrategy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
  @DisplayName("首次预热引导文档时通过统一存储策略写入 docx 和元数据")
  void shouldCreateDefaultBootstrapDocument() throws IOException {
    OnlyofficeIntegrationProperties properties = properties();
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();

    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentMetadataEntity entity = entity("sample", "sample.docx", "native/native/sample.docx", "docx", "word");
    when(metadataService.findDocument("sample")).thenReturn(Optional.empty());
    when(metadataService.createDocument(
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(RequestContext.class),
        nullable(String.class)
    )).thenReturn(entity);
    when(metadataService.requireDocument("sample")).thenReturn(entity);
    when(metadataService.toStoredDocument(any(DocumentMetadataEntity.class), any(), any()))
        .thenAnswer(invocation -> {
          DocumentMetadataEntity actual = invocation.getArgument(0, DocumentMetadataEntity.class);
          Path localPath = invocation.getArgument(1, Path.class);
          return new StoredDocument(
              actual.getDocumentId(),
              actual.getTenantId(),
              actual.getOwnerUser(),
              actual.getSourceSystem(),
              actual.getExternalDocumentId(),
              actual.getTitle(),
              actual.getStorageKey(),
              actual.getFileType(),
              actual.getDocumentType(),
              actual.getStatus(),
              localPath,
              invocation.getArgument(2, java.time.Instant.class),
              null,
              null,
              null,
              null
          );
        });

    DocumentStorageService service = new DocumentStorageService(
        properties,
        metadataService,
        RestClient.builder(),
        List.of(localStrategy),
        resolver,
        keyFactory
    );

    StoredDocument document = service.ensureBootstrapDocument("sample");

    assertEquals("sample.docx", document.title());
    assertEquals("native/native/sample.docx", document.storageKey());
    assertTrue(document.path().toString().replace("\\", "/").endsWith("native/native/sample.docx"));
    assertTrue(java.nio.file.Files.exists(document.path()));
  }

  @Test
  @DisplayName("上传文档后保留扩展名并使用 tenant/sourceSystem/documentId 作为 storage key")
  void shouldStoreUploadedDocumentWithProviderNeutralStorageKey() throws IOException {
    OnlyofficeIntegrationProperties properties = properties();
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();

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
    when(metadataService.requireDocument(anyString()))
        .thenAnswer(invocation -> {
          String documentId = invocation.getArgument(0, String.class);
          return entity(
              documentId,
              documentId + ".xlsx",
              "tenant-a/native/" + documentId + ".xlsx",
              "xlsx",
              "cell"
          );
        });
    when(metadataService.toStoredDocument(any(DocumentMetadataEntity.class), any(), any()))
        .thenAnswer(invocation -> {
          DocumentMetadataEntity actual = invocation.getArgument(0, DocumentMetadataEntity.class);
          Path localPath = invocation.getArgument(1, Path.class);
          return new StoredDocument(
              actual.getDocumentId(),
              actual.getTenantId(),
              actual.getOwnerUser(),
              actual.getSourceSystem(),
              actual.getExternalDocumentId(),
              actual.getTitle(),
              actual.getStorageKey(),
              actual.getFileType(),
              actual.getDocumentType(),
              actual.getStatus(),
              localPath,
              invocation.getArgument(2, java.time.Instant.class),
              null,
              null,
              null,
              null
          );
        });

    DocumentStorageService service = new DocumentStorageService(
        properties,
        metadataService,
        RestClient.builder(),
        List.of(localStrategy),
        resolver,
        keyFactory
    );

    StoredDocument document = service.storeUploadedDocument(
        "sales-report.xlsx",
        "xlsx-demo".getBytes(),
        new RequestContext("tenant-a", "native", "user-a", "Alice")
    );

    assertTrue(document.title().startsWith("sales-report-"));
    assertTrue(document.title().endsWith(".xlsx"));
    assertEquals("xlsx", document.fileType());
    assertEquals("cell", document.documentType());
    assertTrue(document.documentId().startsWith("sales-report-"));
    assertTrue(document.storageKey().startsWith("tenant-a/native/"));
    assertTrue(document.storageKey().endsWith(".xlsx"));
    assertTrue(java.nio.file.Files.exists(document.path()));
  }

  private OnlyofficeIntegrationProperties properties() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.getStorage().getLocal().setRoot(tempDir);
    return properties;
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
    entity.setTenantId(storageKey.startsWith("tenant-a/") ? "tenant-a" : "native");
    entity.setOwnerUser("starter-user");
    entity.setSourceSystem("native");
    entity.setTitle(title);
    entity.setStorageKey(storageKey);
    entity.setFileType(fileType);
    entity.setDocumentType(documentType);
    entity.setStatus("draft");
    return entity;
  }
}
