package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.InsertImageResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlyofficeImageServiceTest {

  @Test
  void shouldBuildSignedInsertImagePayload() {
    DemoProperties properties = new DemoProperties();
    properties.getOnlyoffice().setInternalBaseUrl("http://host.docker.internal:8080");
    properties.getOnlyoffice().setJwtSecret("onlyoffice-demo-secret-2026-03-09-123456");

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
    assertTrue(payload.get("url").toString().contains("/api/documents/demo/images/proxy"));
    assertTrue(payload.get("url").toString().contains("sourceUrl=https://example.com/assets/logo.png"));
    assertNotNull(payload.get("token"));
  }
}
