package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.NormalizedDocumentMetadata;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.impl.DocumentStorageServiceImpl;
import com.earmo.onlyoffice.integration.service.impl.RemoteResourceSecurityServiceImpl;
import com.earmo.onlyoffice.integration.storage.StorageKeyFactory;
import com.earmo.onlyoffice.integration.storage.StorageProviderResolver;
import com.earmo.onlyoffice.integration.storage.local.LocalDocumentStorageStrategy;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    when(metadataService.requireAccessibleDocument("sample")).thenReturn(entity);
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

    DocumentStorageService service = new DocumentStorageServiceImpl(
        properties,
        metadataService,
        RestClient.builder(),
        List.of(localStrategy),
        resolver,
        keyFactory,
        new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
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
    when(metadataService.requireAccessibleDocument(anyString()))
        .thenAnswer(invocation -> {
          String documentId = invocation.getArgument(0, String.class);
          return entity(
              documentId,
              "sales-report.xlsx",
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

    DocumentStorageService service = new DocumentStorageServiceImpl(
        properties,
        metadataService,
        RestClient.builder(),
        List.of(localStrategy),
        resolver,
        keyFactory,
        new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
    );

    StoredDocument document = service.storeUploadedDocument(
        "sales-report.xlsx",
        "xlsx-demo".getBytes(),
        new RequestContext("tenant-a", "native", "user-a", "Alice")
    );

    assertEquals("sales-report.xlsx", document.title());
    assertEquals("xlsx", document.fileType());
    assertEquals("cell", document.documentType());
    assertTrue(document.documentId().matches("[0-9a-hjkmnp-tv-z]{26}"));
    assertTrue(document.storageKey().startsWith("tenant-a/native/"));
    assertTrue(document.storageKey().endsWith(".xlsx"));
    assertTrue(java.nio.file.Files.exists(document.path()));
  }

  @Test
  @DisplayName("读取文档时应自动修复 legacy 扩展名与实际 docx 内容不一致的坏数据")
  void shouldAutoHealLegacyMetadataWhenStoredContentIsDocx() throws IOException {
    OnlyofficeIntegrationProperties properties = properties();
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();

    java.nio.file.Path storedPath = tempDir.resolve("native/native/sample.doc");
    java.nio.file.Files.createDirectories(storedPath.getParent());
    java.nio.file.Files.write(storedPath, minimalDocx());

    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentMetadataEntity legacyEntity = entity("sample", "sample.doc", "native/native/sample.doc", "doc", "word");
    DocumentMetadataEntity normalizedEntity = entity("sample", "sample.docx", "native/native/sample.doc", "docx", "word");
    when(metadataService.requireAccessibleDocument("sample")).thenReturn(legacyEntity);
    when(metadataService.updateDocumentFormat("sample", "sample.docx", "docx", "word")).thenReturn(normalizedEntity);
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

    DocumentStorageService service = new DocumentStorageServiceImpl(
        properties,
        metadataService,
        RestClient.builder(),
        List.of(localStrategy),
        resolver,
        keyFactory,
        new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
    );

    StoredDocument document = service.getRequiredDocument("sample");

    assertEquals("sample.docx", document.title());
    assertEquals("docx", document.fileType());
    verify(metadataService).updateDocumentFormat("sample", "sample.docx", "docx", "word");
  }

  @Test
  @DisplayName("callback 回写 docx 后应同步修正文档元数据")
  void shouldNormalizeMetadataAfterCallbackWriteBack() throws Exception {
    OnlyofficeIntegrationProperties properties = properties();
    properties.getRemoteResource().setAllowPrivateAddressAccess(true);
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();

    java.nio.file.Path storedPath = tempDir.resolve("native/native/sample.doc");
    java.nio.file.Files.createDirectories(storedPath.getParent());
    java.nio.file.Files.write(storedPath, "legacy".getBytes());

    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentMetadataEntity legacyEntity = entity("sample", "sample.doc", "native/native/sample.doc", "doc", "word");
    DocumentMetadataEntity normalizedEntity = entity("sample", "sample.docx", "native/native/sample.doc", "docx", "word");
    when(metadataService.requireAccessibleDocument("sample")).thenReturn(legacyEntity);
    when(metadataService.updateDocumentFormat("sample", "sample.docx", "docx", "word")).thenReturn(normalizedEntity);

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    byte[] latestDocx = minimalDocx();
    try {
      server.createContext("/latest.docx", exchange -> {
        exchange.getResponseHeaders().add(
            "Content-Type",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
        exchange.sendResponseHeaders(200, latestDocx.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(latestDocx);
        }
      });
      server.start();

      DocumentStorageService service = new DocumentStorageServiceImpl(
          properties,
          metadataService,
          RestClient.builder(),
          List.of(localStrategy),
          resolver,
          keyFactory,
          new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
      );

      NormalizedDocumentMetadata metadata = service.saveCallbackDocument(
          "sample",
          "http://localhost:" + server.getAddress().getPort() + "/latest.docx",
          "docx"
      );

      assertEquals("sample.docx", metadata.title());
      assertEquals("docx", metadata.fileType());
      verify(metadataService).updateDocumentFormat("sample", "sample.docx", "docx", "word");
      assertTrue(hasZipPrefix(java.nio.file.Files.readAllBytes(storedPath)));
    } finally {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("远程导入应拒绝回环或内网地址，避免 SSRF")
  void shouldRejectLoopbackRemoteDocumentImport() {
    OnlyofficeIntegrationProperties properties = properties();
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);

    DocumentStorageService service = new DocumentStorageServiceImpl(
        properties,
        metadataService,
        RestClient.builder(),
        List.of(localStrategy),
        resolver,
        keyFactory,
        new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
    );

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> service.importRemoteDocument(
            "http://127.0.0.1/demo.docx",
            new RequestContext("tenant-a", "native", "user-a", "Alice")
        )
    );

    assertTrue(exception.getMessage().contains("不支持访问内网、回环或保留地址"));
  }

  @Test
  @DisplayName("远程导入应校验扩展名和响应媒体类型是否匹配")
  void shouldRejectRemoteDocumentWithUnexpectedMediaType() throws Exception {
    OnlyofficeIntegrationProperties properties = properties();
    properties.getRemoteResource().setAllowPrivateAddressAccess(true);
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    try {
      server.createContext("/fake.docx", exchange -> {
        byte[] body = "not-a-docx".getBytes();
        exchange.getResponseHeaders().add("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(body);
        }
      });
      server.start();

      DocumentStorageService service = new DocumentStorageServiceImpl(
          properties,
          metadataService,
          RestClient.builder(),
          List.of(localStrategy),
          resolver,
          keyFactory,
          new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
      );

      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> service.importRemoteDocument(
              "http://localhost:" + server.getAddress().getPort() + "/fake.docx",
              new RequestContext("tenant-a", "native", "user-a", "Alice")
          )
      );

      assertTrue(exception.getMessage().contains("远程文档类型校验失败"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("远程导入应拒绝超过配置上限的响应体")
  void shouldRejectRemoteDocumentWhenResponseExceedsLimit() throws Exception {
    OnlyofficeIntegrationProperties properties = properties();
    properties.getRemoteResource().setAllowPrivateAddressAccess(true);
    properties.getRemoteResource().setMaxDocumentBytes(8);
    LocalDocumentStorageStrategy localStrategy = new LocalDocumentStorageStrategy(properties);
    StorageProviderResolver resolver = new StorageProviderResolver(properties);
    StorageKeyFactory keyFactory = new StorageKeyFactory();
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    try {
      server.createContext("/large.docx", exchange -> {
        byte[] body = "0123456789".getBytes();
        exchange.getResponseHeaders().add(
            "Content-Type",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(body);
        }
      });
      server.start();

      DocumentStorageService service = new DocumentStorageServiceImpl(
          properties,
          metadataService,
          RestClient.builder(),
          List.of(localStrategy),
          resolver,
          keyFactory,
          new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
      );

      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> service.importRemoteDocument(
              "http://localhost:" + server.getAddress().getPort() + "/large.docx",
              new RequestContext("tenant-a", "native", "user-a", "Alice")
          )
      );

      assertTrue(exception.getMessage().contains("响应超过大小限制"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("远程导入应优先使用响应头中的原始文件名，而不是 URL 路径中的 UUID")
  void shouldPreferContentDispositionFilenameWhenImportingRemoteDocument() throws Exception {
    OnlyofficeIntegrationProperties properties = properties();
    properties.getRemoteResource().setAllowPrivateAddressAccess(true);
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
    when(metadataService.requireAccessibleDocument(anyString()))
        .thenAnswer(invocation -> {
          String documentId = invocation.getArgument(0, String.class);
          return entity(
              documentId,
              "project-plan.docx",
              "tenant-a/native/" + documentId + ".docx",
              "docx",
              "word"
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

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    try {
      server.createContext("/9afd7f75e6ea40b798c6d763a951e28f", exchange -> {
        byte[] body = "docx-demo".getBytes();
        exchange.getResponseHeaders().add(
            "Content-Type",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
        exchange.getResponseHeaders().add(
            "Content-Disposition",
            "attachment; filename=\"project-plan.docx\""
        );
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(body);
        }
      });
      server.start();

      DocumentStorageService service = new DocumentStorageServiceImpl(
          properties,
          metadataService,
          RestClient.builder(),
          List.of(localStrategy),
          resolver,
          keyFactory,
          new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
      );

      StoredDocument document = service.importRemoteDocument(
          "http://localhost:" + server.getAddress().getPort() + "/9afd7f75e6ea40b798c6d763a951e28f",
          new RequestContext("tenant-a", "native", "user-a", "Alice")
      );

      assertEquals("project-plan.docx", document.title());
      assertEquals("docx", document.fileType());
      assertEquals("word", document.documentType());
    } finally {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("远程导入应先解码响应头中的百分号编码文件名")
  void shouldDecodePercentEncodedFilenameWhenImportingRemoteDocument() throws Exception {
    OnlyofficeIntegrationProperties properties = properties();
    properties.getRemoteResource().setAllowPrivateAddressAccess(true);
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
    when(metadataService.requireAccessibleDocument(anyString()))
        .thenAnswer(invocation -> {
          String documentId = invocation.getArgument(0, String.class);
          return entity(
              documentId,
              "+测试文档(1).docx",
              "tenant-a/native/" + documentId + ".docx",
              "docx",
              "word"
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

    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    try {
      server.createContext("/01kn63qdhc1j64t6s4t0vj234d", exchange -> {
        byte[] body = "docx-demo".getBytes();
        exchange.getResponseHeaders().add(
            "Content-Type",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
        exchange.getResponseHeaders().add(
            "Content-Disposition",
            "attachment; filename=\"%2B%E6%B5%8B%E8%AF%95%E6%96%87%E6%A1%A3%281%29.docx\""
        );
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(body);
        }
      });
      server.start();

      DocumentStorageService service = new DocumentStorageServiceImpl(
          properties,
          metadataService,
          RestClient.builder(),
          List.of(localStrategy),
          resolver,
          keyFactory,
          new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
      );

      StoredDocument document = service.importRemoteDocument(
          "http://localhost:" + server.getAddress().getPort() + "/01kn63qdhc1j64t6s4t0vj234d",
          new RequestContext("tenant-a", "native", "user-a", "Alice")
      );

      assertEquals("+测试文档(1).docx", document.title());
      assertEquals("docx", document.fileType());
      assertEquals("word", document.documentType());
    } finally {
      server.stop(0);
    }
  }

  private OnlyofficeIntegrationProperties properties() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.getStorage().getLocal().setRoot(tempDir);
    return properties;
  }

  private byte[] minimalDocx() throws IOException {
    try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
         ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zipOutputStream.write("""
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          </Types>
          """.getBytes());
      zipOutputStream.closeEntry();
      zipOutputStream.putNextEntry(new ZipEntry("word/document.xml"));
      zipOutputStream.write("""
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body><w:p><w:r><w:t>demo</w:t></w:r></w:p></w:body>
          </w:document>
          """.getBytes());
      zipOutputStream.closeEntry();
      zipOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private boolean hasZipPrefix(byte[] body) {
    return body.length >= 4 && body[0] == 'P' && body[1] == 'K' && body[2] == 3 && body[3] == 4;
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
