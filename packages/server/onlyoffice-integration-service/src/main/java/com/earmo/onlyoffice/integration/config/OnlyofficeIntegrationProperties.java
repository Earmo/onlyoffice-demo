package com.earmo.onlyoffice.integration.config;

import com.earmo.onlyoffice.integration.storage.StorageProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

  @Valid
  private CallbackProperties callback = new CallbackProperties();

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

  @Valid
  private AccessContextProperties accessContext = new AccessContextProperties();

  @Valid
  private RemoteResourceProperties remoteResource = new RemoteResourceProperties();

  /**
   * 兼容现有 local 开发路径读取，避免在 Phase 2 的 provider 重构过程中到处直接访问旧字段。
   */
  public Path getStorageRoot() {
    return storage.getLocal().getRoot();
  }

  /**
   * 兼容历史上的 `storage-root` 配置键，避免 Spring 在绑定别名时因为只读 getter 失败。
   */
  public void setStorageRoot(Path storageRoot) {
    storage.getLocal().setRoot(storageRoot);
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

  @Getter
  @Setter
  public static class AccessContextProperties {

    /**
     * 控制哪些 provider 会参与访问上下文解析。
     */
    private List<String> enabledProviders = new ArrayList<>(List.of("header", "jwt", "default"));

    /**
     * 控制 provider 的实际解析顺序。
     */
    private List<String> resolutionOrder = new ArrayList<>(List.of("header", "jwt", "default"));

    /**
     * 是否要求请求必须显式提供访问上下文。
     */
    private boolean requireExplicitContext = true;

    /**
     * 是否允许使用默认值补齐缺失字段，或在关闭严格模式时直接回退默认上下文。
     */
    private boolean allowDefaultContext = false;

    @Valid
    private HeaderAccessContextProperties header = new HeaderAccessContextProperties();

    @Valid
    private JwtAccessContextProperties jwt = new JwtAccessContextProperties();
  }

  @Getter
  @Setter
  public static class HeaderAccessContextProperties {

    private boolean enabled = true;

    @NotBlank
    private String tenantIdHeader = "X-Tenant-Id";

    @NotBlank
    private String sourceSystemHeader = "X-Source-System";

    @NotBlank
    private String externalUserIdHeader = "X-External-User-Id";

    @NotBlank
    private String displayNameHeader = "X-User-Display-Name";

    @NotBlank
    private String permissionsHeader = "X-Access-Permissions";
  }

  @Getter
  @Setter
  public static class JwtAccessContextProperties {

    private boolean enabled = true;

    @NotBlank
    private String headerName = "Authorization";

    @Valid
    private JwtClaimMappings claimMappings = new JwtClaimMappings();
  }

  @Getter
  @Setter
  public static class JwtClaimMappings {

    @NotBlank
    private String tenantId = "tenantId";

    @NotBlank
    private String sourceSystem = "sourceSystem";

    @NotBlank
    private String externalUserId = "externalUserId";

    @NotBlank
    private String displayName = "displayName";

    @NotBlank
    private String permissions = "permissions";
  }

  @Getter
  @Setter
  public static class CallbackProperties {

    @NotBlank
    private String jwtHeaderName = "Authorization";
  }

  @Getter
  @Setter
  public static class RemoteResourceProperties {

    private long maxDocumentBytes = 50L * 1024 * 1024;

    private long maxImageBytes = 10L * 1024 * 1024;

    private boolean allowPrivateAddressAccess = false;
  }
}

