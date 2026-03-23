package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.InsertImageResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlyofficeImageServiceTest {

  @Test
  void shouldBuildSignedInsertImagePayload() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setInternalBaseUrl("http://internal.example.test");
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    OnlyofficeJwtService jwtService = new OnlyofficeJwtService(properties);
    OnlyofficeImageService imageService = new OnlyofficeImageService(
        properties,
        jwtService,
        RestClient.builder()
    );

    InsertImageResponse response = imageService.buildInsertImageResponse(
        "demo",
        "https://example.com/assets/logo.png"
    );

    Map<String, Object> payload = response.insertImage();
    assertEquals("add", payload.get("c"));
    assertEquals("png", payload.get("fileType"));
    assertTrue(payload.get("url").toString().contains("http://internal.example.test/api/documents/demo/images/proxy"));
    assertTrue(payload.get("url").toString().contains("sourceUrl=https://example.com/assets/logo.png"));
    assertNotNull(payload.get("token"));
  }
}


