package com.earmo.onlyoffice.demo.config;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 统一承载文档服务的运行时配置。
 */
@Validated
@ConfigurationProperties(prefix = "demo")
@Getter
@Setter
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
}
