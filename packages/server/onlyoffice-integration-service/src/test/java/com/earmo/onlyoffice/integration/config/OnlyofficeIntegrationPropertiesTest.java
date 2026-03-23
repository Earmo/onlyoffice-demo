package com.earmo.onlyoffice.integration.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlyofficeIntegrationPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class)
      .withPropertyValues(
          "onlyoffice.integration.public-base-url=https://api.example.test",
          "onlyoffice.integration.internal-base-url=http://internal.example.test",
          "onlyoffice.integration.document-server-url=https://docs.example.test",
          "onlyoffice.integration.jwt-secret=test-secret-1234567890",
          "onlyoffice.integration.default-language=en",
          "onlyoffice.integration.default-region=en-US",
          "onlyoffice.integration.default-tenant-id=tenant-a",
          "onlyoffice.integration.default-source-system=erp",
          "onlyoffice.integration.default-user=user-a",
          "onlyoffice.integration.default-user-name=Alice",
          "onlyoffice.integration.storage-root=./tmp/storage"
      );

  @Test
  void shouldBindServiceRuntimeProperties() {
    contextRunner.run(context -> {
      OnlyofficeIntegrationProperties properties = context.getBean(OnlyofficeIntegrationProperties.class);
      assertEquals("https://api.example.test", properties.getPublicBaseUrl());
      assertEquals("http://internal.example.test", properties.getInternalBaseUrl());
      assertEquals("https://docs.example.test", properties.getDocumentServerUrl());
      assertEquals("test-secret-1234567890", properties.getJwtSecret());
      assertEquals("en", properties.getDefaultLanguage());
      assertEquals("en-US", properties.getDefaultRegion());
      assertEquals("tenant-a", properties.getDefaultTenantId());
      assertEquals("erp", properties.getDefaultSourceSystem());
      assertEquals("user-a", properties.getDefaultUser());
      assertEquals("Alice", properties.getDefaultUserName());
      assertEquals("tmp/storage", properties.getStorageRoot().toString().replace("\\", "/"));
    });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OnlyofficeIntegrationProperties.class)
  static class TestConfig {
  }
}
