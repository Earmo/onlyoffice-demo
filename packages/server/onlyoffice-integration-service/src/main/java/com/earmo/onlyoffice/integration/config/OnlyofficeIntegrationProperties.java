package com.earmo.onlyoffice.integration.config;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 统一承载 starter 服务的运行时配置。
 *
 * <p>这里显式使用 `onlyoffice.integration` 前缀，目的是把历史上的 demo 配置根完全收口，
 * 让独立部署和被其他系统嵌入时都能围绕同一套配置语义工作。
 */
@Validated
@ConfigurationProperties(prefix = "onlyoffice.integration")
@Getter
@Setter
public class OnlyofficeIntegrationProperties {

  private String publicBaseUrl = "";

  @NotBlank
  private String internalBaseUrl = "http://host.docker.internal:8080";

  private String documentServerUrl = "";

  @NotBlank
  private String jwtSecret = "onlyoffice-integration-secret-2026-03-09-123456";

  @NotBlank
  private String defaultLanguage = "zh";

  @NotBlank
  private String defaultRegion = "zh-CN";

  @NotBlank
  private String defaultTenantId = "native";

  @NotBlank
  private String defaultSourceSystem = "native";

  @NotBlank
  private String defaultUser = "starter-user";

  @NotBlank
  private String defaultUserName = "默认用户";

  private Path storageRoot = Path.of("./storage");
}

