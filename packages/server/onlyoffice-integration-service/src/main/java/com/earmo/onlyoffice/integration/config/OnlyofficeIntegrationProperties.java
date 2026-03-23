package com.earmo.onlyoffice.integration.config;

import com.earmo.onlyoffice.integration.storage.StorageProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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

  @Valid
  private StorageProperties storage = new StorageProperties();

  /**
   * 兼容现有 local 开发路径读取，避免在 Phase 2 的 provider 重构过程中到处直接访问旧字段。
   */
  public Path getStorageRoot() {
    return storage.getLocal().getRoot();
  }

  @Getter
  @Setter
  public static class StorageProperties {

    private StorageProvider defaultProvider = StorageProvider.LOCAL;

    @Valid
    private RoutingProperties routing = new RoutingProperties();

    @Valid
    private LocalStorageProperties local = new LocalStorageProperties();

    @Valid
    private MinioStorageProperties minio = new MinioStorageProperties();
  }

  @Getter
  @Setter
  public static class RoutingProperties {

    private Map<String, StorageProvider> tenants = new LinkedHashMap<>();

    private Map<String, StorageProvider> sourceSystems = new LinkedHashMap<>();
  }

  @Getter
  @Setter
  public static class LocalStorageProperties {

    private Path root = Path.of("./storage");
  }

  @Getter
  @Setter
  public static class MinioStorageProperties {

    @NotBlank
    private String endpoint = "http://localhost:9000";

    @NotBlank
    private String bucket = "onlyoffice-documents";

    @NotBlank
    private String accessKey = "onlyoffice";

    @NotBlank
    private String secretKey = "onlyoffice123";

    private boolean pathStyleAccess = true;

    private boolean secure = false;
  }
}

