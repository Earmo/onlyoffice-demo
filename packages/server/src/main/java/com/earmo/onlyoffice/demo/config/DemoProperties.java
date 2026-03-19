package com.earmo.onlyoffice.demo.config;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 统一承载文档服务的运行时配置。
 */
@Validated
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

  private String publicBaseUrl = "";

  @NotBlank
  private String internalBaseUrl = "http://host.docker.internal:8080";

  private String documentServerUrl = "";

  @NotBlank
  private String jwtSecret = "onlyoffice-demo-secret-2026-03-09-123456";

  @NotBlank
  private String defaultLanguage = "zh";

  @NotBlank
  private String defaultRegion = "zh-CN";

  @NotBlank
  private String defaultTenantId = "native";

  @NotBlank
  private String defaultSourceSystem = "native";

  @NotBlank
  private String defaultUserId = "demo-user";

  @NotBlank
  private String defaultUserName = "演示用户";

  private Path storageRoot = Path.of("./storage");

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public String getInternalBaseUrl() {
    return internalBaseUrl;
  }

  public void setInternalBaseUrl(String internalBaseUrl) {
    this.internalBaseUrl = internalBaseUrl;
  }

  public String getDocumentServerUrl() {
    return documentServerUrl;
  }

  public void setDocumentServerUrl(String documentServerUrl) {
    this.documentServerUrl = documentServerUrl;
  }

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public String getDefaultLanguage() {
    return defaultLanguage;
  }

  public void setDefaultLanguage(String defaultLanguage) {
    this.defaultLanguage = defaultLanguage;
  }

  public String getDefaultRegion() {
    return defaultRegion;
  }

  public void setDefaultRegion(String defaultRegion) {
    this.defaultRegion = defaultRegion;
  }

  public String getDefaultTenantId() {
    return defaultTenantId;
  }

  public void setDefaultTenantId(String defaultTenantId) {
    this.defaultTenantId = defaultTenantId;
  }

  public String getDefaultSourceSystem() {
    return defaultSourceSystem;
  }

  public void setDefaultSourceSystem(String defaultSourceSystem) {
    this.defaultSourceSystem = defaultSourceSystem;
  }

  public String getDefaultUserId() {
    return defaultUserId;
  }

  public void setDefaultUserId(String defaultUserId) {
    this.defaultUserId = defaultUserId;
  }

  public String getDefaultUserName() {
    return defaultUserName;
  }

  public void setDefaultUserName(String defaultUserName) {
    this.defaultUserName = defaultUserName;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(Path storageRoot) {
    this.storageRoot = storageRoot;
  }
}
