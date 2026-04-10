package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.impl.OnlyofficeConfigServiceImpl;
import com.earmo.onlyoffice.integration.service.impl.OnlyofficeJwtServiceImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnlyofficeConfigServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldBuildSignedEditorConfig() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://api.example.test");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setDocumentServerUrl("https://docs.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );

    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeJwtService jwtService = new OnlyofficeJwtServiceImpl(properties);
    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(properties, storageService, jwtService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/demo/editor-config");
    request.setScheme("https");
    request.setServerName("app.example.test");
    request.setServerPort(443);

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        false,
        new AccessContext(
            "tenant-a",
            "native",
            "user-a",
            "Alice",
            Map.of("edit", true, "comment", true, "download", true, "print", false),
            "header"
        ),
        request
    );
    Map<String, Object> config = response.config();
    Map<String, Object> document = cast(config.get("document"));
    Map<String, Object> editorConfig = cast(config.get("editorConfig"));
    Map<String, Object> permissions = cast(document.get("permissions"));
    Map<String, Object> customization = cast(editorConfig.get("customization"));
    Map<String, Object> layout = cast(customization.get("layout"));
    Map<String, Object> leftMenu = cast(layout.get("leftMenu"));
    Map<String, Object> plugins = cast(editorConfig.get("plugins"));

    assertEquals("https://docs.example.test/", response.documentServerUrl());
    assertTrue(document.get("url").toString().contains("http://internal.example.test/api/documents/demo/file.docx"));
    assertEquals("demo-1773907200000", document.get("key"));
    assertTrue(editorConfig.get("callbackUrl").toString().contains("http://internal.example.test/api/documents/demo/callback"));
    assertEquals("user-a", cast(editorConfig.get("user")).get("id"));
    assertEquals("Alice", cast(editorConfig.get("user")).get("name"));
    assertEquals("edit", editorConfig.get("mode"));
    assertEquals(Boolean.TRUE, permissions.get("edit"));
    assertEquals(Boolean.TRUE, permissions.get("comment"));
    assertEquals(Boolean.TRUE, permissions.get("download"));
    assertEquals(Boolean.FALSE, permissions.get("print"));
    assertEquals(Boolean.FALSE, customization.get("compactToolbar"));
    assertEquals(Boolean.FALSE, customization.get("toolbarNoTabs"));
    assertEquals(Boolean.TRUE, leftMenu.get("mode"));
    assertEquals(Boolean.TRUE, leftMenu.get("navigation"));
    assertEquals(List.of("asc.{A4B0E7D2-6A7B-4E21-9C1A-7F4F31C6B201}"), plugins.get("autostart"));
    assertEquals(
        List.of("https://api.example.test/onlyoffice-plugins/ai-bridge/config.json"),
        plugins.get("pluginsData")
    );
    assertNotNull(config.get("token"));
  }

  @Test
  void shouldFallbackToPublicBaseUrlWhenDocumentServerUrlIsBlank() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://gateway.example.test");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );

    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        true,
        new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
        null
    );

    assertEquals("https://gateway.example.test/", response.documentServerUrl());
    assertEquals("view", cast(response.config().get("editorConfig")).get("mode"));
    assertEquals(null, cast(response.config().get("editorConfig")).get("plugins"));
  }

  @Test
  void shouldForceViewModeWhenEditPermissionIsFalse() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://gateway.example.test");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );
    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        false,
        new AccessContext(
            "tenant-a",
            "native",
            "user-a",
            "Alice",
            Map.of("edit", false, "comment", false, "download", false, "print", true),
            "jwt"
        ),
        new MockHttpServletRequest()
    );

    Map<String, Object> config = response.config();
    Map<String, Object> document = cast(config.get("document"));
    Map<String, Object> editorConfig = cast(config.get("editorConfig"));
    Map<String, Object> permissions = cast(document.get("permissions"));

    assertEquals("view", editorConfig.get("mode"));
    assertEquals(Boolean.FALSE, permissions.get("edit"));
    assertEquals(Boolean.FALSE, permissions.get("download"));
    assertEquals(Boolean.FALSE, permissions.get("comment"));
    assertEquals(Boolean.TRUE, permissions.get("print"));
    assertEquals(null, editorConfig.get("plugins"));
  }

  @Test
  void shouldUseConfiguredDocumentServerUrlEvenWhenRequestContainsForwardedHeaders() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://gateway.example.test");
    properties.setDocumentServerUrl("https://docs.example.test/api/office");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );
    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/demo/editor-config");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "docs.example.test");
    request.addHeader("X-Forwarded-Port", "8443");

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        false,
        new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
        request
    );

    assertEquals("https://docs.example.test/api/office/", response.documentServerUrl());
    Map<String, Object> plugins = cast(cast(response.config().get("editorConfig")).get("plugins"));
    assertEquals(
        List.of("https://gateway.example.test/onlyoffice-plugins/ai-bridge/config.json"),
        plugins.get("pluginsData")
    );
  }

  @Test
  void shouldKeepEditingDocumentKeyStableAcrossCallbackSaves() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://gateway.example.test");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setDocumentServerUrl("https://docs.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "editing",
        path,
        Instant.parse("2026-03-25T10:00:05Z"),
        Instant.parse("2026-03-25T09:59:00Z"),
        Instant.parse("2026-03-25T10:00:04Z"),
        6,
        null
    );

    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        false,
        new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
        new MockHttpServletRequest()
    );

    Map<String, Object> document = cast(response.config().get("document"));
    assertEquals("demo-1774432740000", document.get("key"));
  }

  @Test
  void shouldFailFastWhenRuntimeUrlsAreMissingAndRequestOriginIsUnavailable() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("");
    properties.setDocumentServerUrl("");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );
    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> configService.buildEditorConfig(
            "demo",
            false,
            new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
            null
        )
    );

    assertTrue(exception.getMessage().contains("public-base-url"));
  }

  @Test
  void shouldExposeDocumentDownloadUrlWithFileExtension() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://gateway.example.test");
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("report.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "report.docx",
        "documents/report.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );
    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        false,
        new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
        new MockHttpServletRequest()
    );

    Map<String, Object> document = cast(response.config().get("document"));
    assertEquals("http://internal.example.test/api/documents/demo/file.docx", document.get("url"));
  }

  @Test
  void shouldFailFastWhenInternalBaseUrlUsesUnsupportedScheme() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setPublicBaseUrl("https://gateway.example.test");
    properties.setDocumentServerUrl("https://docs.example.test");
    properties.setInternalBaseUrl("ftp://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    Path path = tempDir.resolve("demo.docx");
    Files.writeString(path, "demo");
    StoredDocument storedDocument = new StoredDocument(
        "demo",
        "tenant-a",
        "user-a",
        "native",
        null,
        "demo.docx",
        "documents/demo.docx",
        "docx",
        "word",
        "draft",
        path,
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );
    DocumentStorageService storageService = mock(DocumentStorageService.class);
    when(storageService.getRequiredDocument("demo")).thenReturn(storedDocument);

    OnlyofficeConfigService configService = new OnlyofficeConfigServiceImpl(
        properties,
        storageService,
        new OnlyofficeJwtServiceImpl(properties)
    );

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> configService.buildEditorConfig(
            "demo",
            false,
            new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
            new MockHttpServletRequest()
        )
    );

    assertTrue(exception.getMessage().contains("internal-base-url"));
    assertTrue(exception.getMessage().contains("http/https"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> cast(Object value) {
    return (Map<String, Object>) value;
  }
}
