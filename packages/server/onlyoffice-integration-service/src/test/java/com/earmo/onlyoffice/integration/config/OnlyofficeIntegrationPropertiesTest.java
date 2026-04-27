package com.earmo.onlyoffice.integration.config;

import com.earmo.onlyoffice.integration.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
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
          "onlyoffice.integration.storage.default-provider=minio",
          "onlyoffice.integration.storage.local.root=./tmp/storage",
          "onlyoffice.integration.storage.minio.endpoint=http://minio.example.test:9000",
          "onlyoffice.integration.storage.minio.bucket=docs",
          "onlyoffice.integration.storage.minio.access-key=minio-user",
          "onlyoffice.integration.storage.minio.secret-key=minio-pass",
          "onlyoffice.integration.storage.cos.region=ap-guangzhou",
          "onlyoffice.integration.storage.cos.bucket=cos-docs-1250000000",
          "onlyoffice.integration.storage.cos.secret-id=cos-secret-id",
          "onlyoffice.integration.storage.cos.secret-key=cos-secret-key",
          "onlyoffice.integration.storage.cos.endpoint-suffix=cos.internal.example",
          "onlyoffice.integration.storage.routing.tenants.tenant-a=minio",
          "onlyoffice.integration.storage.routing.source-systems.erp=local",
          "onlyoffice.integration.editing-session.active-timeout-seconds=30",
          "onlyoffice.integration.editing-session.runtime-keepalive-seconds=10"
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
      assertEquals(StorageProvider.MINIO, properties.getStorage().getDefaultProvider());
      assertEquals("tmp/storage", properties.getStorage().getLocal().getRoot().toString().replace("\\", "/"));
      assertEquals("http://minio.example.test:9000", properties.getStorage().getMinio().getEndpoint());
      assertEquals("docs", properties.getStorage().getMinio().getBucket());
      assertEquals("minio-user", properties.getStorage().getMinio().getAccessKey());
      assertEquals("minio-pass", properties.getStorage().getMinio().getSecretKey());
      assertEquals("ap-guangzhou", properties.getStorage().getCos().getRegion());
      assertEquals("cos-docs-1250000000", properties.getStorage().getCos().getBucket());
      assertEquals("cos-secret-id", properties.getStorage().getCos().getSecretId());
      assertEquals("cos-secret-key", properties.getStorage().getCos().getSecretKey());
      assertEquals("cos.internal.example", properties.getStorage().getCos().getEndpointSuffix());
      assertEquals(StorageProvider.MINIO, properties.getStorage().getRouting().getTenants().get("tenant-a"));
      assertEquals(StorageProvider.LOCAL, properties.getStorage().getRouting().getSourceSystems().get("erp"));
      assertEquals(30L, properties.getEditingSession().getActiveTimeoutSeconds());
      assertEquals(10L, properties.getEditingSession().getRuntimeKeepaliveSeconds());
    });
  }

  @Test
  void shouldRejectUnsafeRuntimeKeepaliveInterval() {
    contextRunner
        .withPropertyValues(
            "onlyoffice.integration.editing-session.active-timeout-seconds=10",
            "onlyoffice.integration.editing-session.runtime-keepalive-seconds=8"
        )
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OnlyofficeIntegrationProperties.class)
  static class TestConfig {
  }
}
