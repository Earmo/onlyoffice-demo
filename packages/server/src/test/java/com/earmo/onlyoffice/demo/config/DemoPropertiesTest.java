package com.earmo.onlyoffice.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class)
      .withPropertyValues(
          "demo.public-base-url=https://api.example.test",
          "demo.internal-base-url=http://internal.example.test",
          "demo.document-server-url=https://docs.example.test",
          "demo.jwt-secret=test-secret-1234567890",
          "demo.default-language=en",
          "demo.default-region=en-US",
          "demo.default-tenant-id=tenant-a",
          "demo.default-source-system=erp",
          "demo.default-user-id=user-a",
          "demo.default-user-name=Alice",
          "demo.storage-root=./tmp/storage"
      );

  @Test
  void shouldBindServiceRuntimeProperties() {
    contextRunner.run(context -> {
      DemoProperties properties = context.getBean(DemoProperties.class);
      assertEquals("https://api.example.test", properties.getPublicBaseUrl());
      assertEquals("http://internal.example.test", properties.getInternalBaseUrl());
      assertEquals("https://docs.example.test", properties.getDocumentServerUrl());
      assertEquals("test-secret-1234567890", properties.getJwtSecret());
      assertEquals("en", properties.getDefaultLanguage());
      assertEquals("en-US", properties.getDefaultRegion());
      assertEquals("tenant-a", properties.getDefaultTenantId());
      assertEquals("erp", properties.getDefaultSourceSystem());
      assertEquals("user-a", properties.getDefaultUserId());
      assertEquals("Alice", properties.getDefaultUserName());
      assertEquals("tmp/storage", properties.getStorageRoot().toString().replace("\\", "/"));
    });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DemoProperties.class)
  static class TestConfig {
  }
}
