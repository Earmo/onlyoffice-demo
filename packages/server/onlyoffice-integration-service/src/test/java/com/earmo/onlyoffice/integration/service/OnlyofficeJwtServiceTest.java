package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnlyofficeJwtServiceTest {

  @Test
  void shouldVerifySignedCallbackToken() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    OnlyofficeJwtService jwtService = new OnlyofficeJwtService(properties);
    String token = jwtService.sign(Map.of("documentId", "demo", "status", 2));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);

    assertEquals("demo", jwtService.verifyCallbackRequest(request).get("documentId", String.class));
  }

  @Test
  void shouldRejectInvalidCallbackToken() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

    OnlyofficeJwtService jwtService = new OnlyofficeJwtService(properties);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer invalid-token");

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> jwtService.verifyCallbackRequest(request)
    );

    assertEquals("ONLYOFFICE callback JWT 校验失败：签名无效。", exception.getMessage());
  }
}
