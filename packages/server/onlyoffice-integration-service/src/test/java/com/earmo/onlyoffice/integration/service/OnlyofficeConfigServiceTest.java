package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    OnlyofficeJwtService jwtService = new OnlyofficeJwtService(properties);
    OnlyofficeConfigService configService = new OnlyofficeConfigService(properties, storageService, jwtService);
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

    assertEquals("https://docs.example.test/", response.documentServerUrl());
    assertTrue(document.get("url").toString().contains("http://internal.example.test/api/documents/demo/file"));
    assertTrue(editorConfig.get("callbackUrl").toString().contains("http://internal.example.test/api/documents/demo/callback"));
    assertEquals("user-a", cast(editorConfig.get("user")).get("id"));
    assertEquals("Alice", cast(editorConfig.get("user")).get("name"));
    assertEquals("edit", editorConfig.get("mode"));
    assertEquals(Boolean.TRUE, permissions.get("edit"));
    assertEquals(Boolean.TRUE, permissions.get("comment"));
    assertEquals(Boolean.TRUE, permissions.get("download"));
    assertEquals(Boolean.FALSE, permissions.get("print"));
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

    OnlyofficeConfigService configService = new OnlyofficeConfigService(
        properties,
        storageService,
        new OnlyofficeJwtService(properties)
    );

    EditorConfigResponse response = configService.buildEditorConfig(
        "demo",
        true,
        new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "header"),
        new MockHttpServletRequest()
    );

    assertEquals("https://gateway.example.test/", response.documentServerUrl());
    assertEquals("view", cast(response.config().get("editorConfig")).get("mode"));
  }

  @Test
  void shouldForceViewModeWhenEditPermissionIsFalse() throws IOException {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
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

    OnlyofficeConfigService configService = new OnlyofficeConfigService(
        properties,
        storageService,
        new OnlyofficeJwtService(properties)
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
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> cast(Object value) {
    return (Map<String, Object>) value;
  }
}


