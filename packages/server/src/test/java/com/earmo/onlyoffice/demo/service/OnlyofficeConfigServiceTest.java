package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.EditorConfigResponse;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlyofficeConfigServiceTest {

  @TempDir
  java.nio.file.Path tempDir;

  @Test
  void shouldBuildSignedEditorConfig() throws IOException {
    DemoProperties properties = new DemoProperties();
    properties.setStorageRoot(tempDir);
    properties.getOnlyoffice().setDocumentServerUrl("http://docs.example.com");
    properties.getOnlyoffice().setInternalBaseUrl("http://host.docker.internal:8080");
    properties.getOnlyoffice().setJwtSecret("onlyoffice-demo-secret-2026-03-09-123456");

    DocumentStorageService storageService = new DocumentStorageService(properties, RestClient.builder());
    OnlyofficeJwtService jwtService = new OnlyofficeJwtService(properties);
    OnlyofficeConfigService configService = new OnlyofficeConfigService(properties, storageService, jwtService);
    MockHttpServletRequest request = new MockHttpServletRequest();

    EditorConfigResponse response = configService.buildEditorConfig("demo", false, request);
    Map<String, Object> config = response.config();
    Map<String, Object> document = cast(config.get("document"));
    Map<String, Object> editorConfig = cast(config.get("editorConfig"));
    Map<String, Object> permissions = cast(document.get("permissions"));
    Map<String, Object> customization = cast(editorConfig.get("customization"));
    Map<String, Object> layout = cast(customization.get("layout"));
    Map<String, Object> leftMenu = cast(layout.get("leftMenu"));
    Map<String, Object> toolbar = cast(layout.get("toolbar"));
    Map<String, Object> viewToolbar = cast(toolbar.get("view"));

    assertEquals("http://docs.example.com/", response.documentServerUrl());
    assertEquals("word", config.get("documentType"));
    assertTrue(document.get("url").toString().contains("/api/documents/demo/file"));
    assertTrue(editorConfig.get("callbackUrl").toString().contains("/api/documents/demo/callback"));
    assertEquals("edit", editorConfig.get("mode"));
    assertEquals(Boolean.TRUE, permissions.get("edit"));
    assertEquals(Boolean.TRUE, leftMenu.get("mode"));
    assertEquals(Boolean.TRUE, leftMenu.get("navigation"));
    assertEquals(Boolean.TRUE, viewToolbar.get("navigation"));
    assertNotNull(config.get("token"));
  }

  @Test
  void shouldBuildReadonlyEditorConfig() throws IOException {
    DemoProperties properties = new DemoProperties();
    properties.setStorageRoot(tempDir);
    properties.getOnlyoffice().setDocumentServerUrl("");
    properties.getOnlyoffice().setInternalBaseUrl("http://host.docker.internal:8080");
    properties.getOnlyoffice().setJwtSecret("onlyoffice-demo-secret-2026-03-09-123456");

    DocumentStorageService storageService = new DocumentStorageService(properties, RestClient.builder());
    OnlyofficeJwtService jwtService = new OnlyofficeJwtService(properties);
    OnlyofficeConfigService configService = new OnlyofficeConfigService(properties, storageService, jwtService);
    MockHttpServletRequest request = new MockHttpServletRequest();
    EditorConfigResponse response = configService.buildEditorConfig("demo", true, request);
    Map<String, Object> config = response.config();
    Map<String, Object> document = cast(config.get("document"));
    Map<String, Object> editorConfig = cast(config.get("editorConfig"));
    Map<String, Object> permissions = cast(document.get("permissions"));

    assertEquals("/", response.documentServerUrl());
    assertEquals("view", editorConfig.get("mode"));
    assertEquals(Boolean.FALSE, permissions.get("edit"));
    assertEquals(Boolean.FALSE, permissions.get("review"));
    assertEquals(Boolean.FALSE, permissions.get("fillForms"));
    assertNotNull(config.get("token"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> cast(Object value) {
    return (Map<String, Object>) value;
  }
}
