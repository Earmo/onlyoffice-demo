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

  /** 浏览器或外部系统访问本服务时看到的公开地址。 */
  private String publicBaseUrl = "";

  /** ONLYOFFICE 容器访问文件下载与 callback 接口时使用的内部地址。 */
  @NotBlank
  private String internalBaseUrl = "http://host.docker.internal:8080";

  /** 浏览器加载 ONLYOFFICE Docs 静态资源时使用的地址。 */
  private String documentServerUrl = "";

  /** editor-config 签名与 callback 验签共用的 JWT 密钥。 */
  @NotBlank
  private String jwtSecret = "onlyoffice-integration-secret-2026-03-09-123456";

  /** callback 相关配置。 */
  @Valid
  private CallbackProperties callback = new CallbackProperties();

  /** 默认编辑语言。 */
  @NotBlank
  private String defaultLanguage = "zh";

  /** 默认区域设置。 */
  @NotBlank
  private String defaultRegion = "zh-CN";

  /** 缺省租户 ID。 */
  @NotBlank
  private String defaultTenantId = "native";

  /** 缺省来源系统。 */
  @NotBlank
  private String defaultSourceSystem = "native";

  /** 缺省用户 ID。 */
  @NotBlank
  private String defaultUser = "starter-user";

  /** 缺省用户显示名。 */
  @NotBlank
  private String defaultUserName = "默认用户";

  /** 存储策略配置。 */
  @Valid
  private StorageProperties storage = new StorageProperties();

  /** 访问上下文解析配置。 */
  @Valid
  private AccessContextProperties accessContext = new AccessContextProperties();

  /** 远程资源安全限制配置。 */
  @Valid
  private RemoteResourceProperties remoteResource = new RemoteResourceProperties();

  /** 编辑会话运行态配置。 */
  @Valid
  private EditingSessionProperties editingSession = new EditingSessionProperties();

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

    /** 默认存储策略。 */
    private StorageProvider defaultProvider = StorageProvider.LOCAL;

    /** 按 tenant/sourceSystem 路由存储策略的配置。 */
    @Valid
    private RoutingProperties routing = new RoutingProperties();

    /** 本地文件系统存储配置。 */
    @Valid
    private LocalStorageProperties local = new LocalStorageProperties();

    /** MinIO 存储配置。 */
    @Valid
    private MinioStorageProperties minio = new MinioStorageProperties();

    /** 腾讯云 COS 存储配置。 */
    @Valid
    private CosStorageProperties cos = new CosStorageProperties();
  }

  @Getter
  @Setter
  public static class RoutingProperties {

    /** tenantId -> provider 的映射配置。 */
    private Map<String, StorageProvider> tenants = new LinkedHashMap<>();

    /** sourceSystem -> provider 的映射配置。 */
    private Map<String, StorageProvider> sourceSystems = new LinkedHashMap<>();
  }

  @Getter
  @Setter
  public static class LocalStorageProperties {

    /** local 存储策略的根目录。 */
    private Path root = Path.of("./storage");
  }

  @Getter
  @Setter
  public static class MinioStorageProperties {

    /** MinIO 服务地址。 */
    @NotBlank
    private String endpoint = "http://localhost:9000";

    /** MinIO bucket 名称。 */
    @NotBlank
    private String bucket = "onlyoffice-documents";

    /** MinIO Access Key。 */
    @NotBlank
    private String accessKey = "onlyoffice";

    /** MinIO Secret Key。 */
    @NotBlank
    private String secretKey = "onlyoffice123";

    /** 是否使用 path-style 访问。 */
    private boolean pathStyleAccess = true;

    /** 是否通过 HTTPS 访问 MinIO。 */
    private boolean secure = false;
  }

  @Getter
  @Setter
  public static class CosStorageProperties {

    /** COS 所在地域，例如 `ap-guangzhou`。 */
    @NotBlank
    private String region = "ap-guangzhou";

    /** COS bucket 名称，通常应包含 appId 后缀。 */
    @NotBlank
    private String bucket = "onlyoffice-documents-1250000000";

    /** 腾讯云 SecretId。 */
    @NotBlank
    private String secretId = "placeholder-secret-id";

    /** 腾讯云 SecretKey。 */
    @NotBlank
    private String secretKey = "placeholder-secret-key";

    /** COS 访问域名后缀，默认使用腾讯云公有云域名。 */
    private String endpointSuffix = "cos.myqcloud.com";
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

    /** 是否启用 Header provider。 */
    private boolean enabled = true;

    /** 读取租户 ID 的请求头名称。 */
    @NotBlank
    private String tenantIdHeader = "X-Tenant-Id";

    /** 读取来源系统的请求头名称。 */
    @NotBlank
    private String sourceSystemHeader = "X-Source-System";

    /** 读取外部用户 ID 的请求头名称。 */
    @NotBlank
    private String externalUserIdHeader = "X-External-User-Id";

    /** 读取用户显示名的请求头名称。 */
    @NotBlank
    private String displayNameHeader = "X-User-Display-Name";

    /** 读取最小权限集合的请求头名称。 */
    @NotBlank
    private String permissionsHeader = "X-Access-Permissions";
  }

  @Getter
  @Setter
  public static class JwtAccessContextProperties {

    /** 是否启用 JWT provider。 */
    private boolean enabled = true;

    /** JWT provider 读取 token 的请求头名称。 */
    @NotBlank
    private String headerName = "Authorization";

    /** JWT claim 名称映射。 */
    @Valid
    private JwtClaimMappings claimMappings = new JwtClaimMappings();
  }

  @Getter
  @Setter
  public static class JwtClaimMappings {

    /** tenantId 对应的 claim 名称。 */
    @NotBlank
    private String tenantId = "tenantId";

    /** sourceSystem 对应的 claim 名称。 */
    @NotBlank
    private String sourceSystem = "sourceSystem";

    /** externalUserId 对应的 claim 名称。 */
    @NotBlank
    private String externalUserId = "externalUserId";

    /** displayName 对应的 claim 名称。 */
    @NotBlank
    private String displayName = "displayName";

    /** permissions 对应的 claim 名称。 */
    @NotBlank
    private String permissions = "permissions";
  }

  @Getter
  @Setter
  public static class CallbackProperties {

    /** callback JWT 默认读取的请求头名称。 */
    @NotBlank
    private String jwtHeaderName = "Authorization";
  }

  @Getter
  @Setter
  public static class RemoteResourceProperties {

    /** 远程导入文档允许的最大字节数。 */
    private long maxDocumentBytes = 50L * 1024 * 1024;

    /** 图片代理允许的最大字节数。 */
    private long maxImageBytes = 10L * 1024 * 1024;

    /** 是否允许访问私网/本地地址。 */
    private boolean allowPrivateAddressAccess = false;
  }

  @Getter
  @Setter
  public static class EditingSessionProperties {

    /** 活跃编辑会话的心跳超时秒数；超过该窗口且未显式关闭，则不再对列表投影为 editing。 */
    private long activeTimeoutSeconds = 30L;
  }
}

