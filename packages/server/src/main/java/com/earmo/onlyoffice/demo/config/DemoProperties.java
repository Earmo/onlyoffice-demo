package com.earmo.onlyoffice.demo.config;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 统一承载示例项目的自定义配置。
 *
 * <p>这里把 ONLYOFFICE 相关配置和本地存储根目录集中起来，方便通过
 * application.yml 或环境变量整体覆盖。
 */
@Validated
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

  /**
   * 本地文件存储根目录。
   *
   * <p>当前示例不接对象存储，也不接数据库，所以直接把文档保存在本地文件系统。
   */
  private Path storageRoot = Path.of("./storage");

  /**
   * ONLYOFFICE 集成所需的运行参数。
   *
   * <p>单独放成嵌套对象，是为了让配置结构更接近 demo.onlyoffice.*。
   */
  private final Onlyoffice onlyoffice = new Onlyoffice();

  public Path getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  public Onlyoffice getOnlyoffice() {
    return onlyoffice;
  }

  public static class Onlyoffice {

    /**
     * 浏览器访问 ONLYOFFICE Docs 的公开地址。
     *
     * <p>留空时表示按当前请求自动推导，这样部署到公网域名、动态 IP 或反向代理后，
     * 就不需要为每个访问入口单独改配置。
     */
    private String documentServerUrl = "";

    /**
     * ONLYOFFICE 容器回调当前后端、下载文档时使用的后端地址。
     *
     * <p>本地 Docker Desktop 场景下使用 host.docker.internal 是最省事的做法，
     * 因为容器内无法直接访问宿主机上的 localhost。
     */
    @NotBlank
    private String internalBaseUrl = "http://host.docker.internal:8080";

    /**
     * 给 ONLYOFFICE 配置对象签名使用的共享密钥。
     *
     * <p>这个值需要和 ONLYOFFICE Docs 容器里的 JWT 配置保持一致，否则编辑器不会接受配置。
     */
    @NotBlank
    private String jwtSecret = "onlyoffice-demo-secret-2026-03-09-123456";

    /**
     * ONLYOFFICE 编辑器界面语言。
     *
     * <p>根据 ONLYOFFICE 官方文档，简体中文可使用 zh，繁体中文使用 zh-TW。
     */
    @NotBlank
    private String defaultLanguage = "zh";

    /**
     * 地区代码。
     *
     * <p>这个值主要影响电子表格中的日期、时间、货币格式，以及部分编辑器的默认度量单位。
     */
    @NotBlank
    private String defaultRegion = "zh-CN";

    /**
     * 注入到 ONLYOFFICE editorConfig.user 中的默认用户 ID。
     *
     * <p>示例里没有真实登录态，因此用固定演示用户占位。
     */
    @NotBlank
    private String defaultUserId = "demo-user";

    /**
     * 注入到 ONLYOFFICE editorConfig.user 中的默认用户名。
     */
    @NotBlank
    private String defaultUserName = "演示用户";

    public String getDocumentServerUrl() {
      return documentServerUrl;
    }

    public void setDocumentServerUrl(String documentServerUrl) {
      this.documentServerUrl = documentServerUrl;
    }

    public String getInternalBaseUrl() {
      return internalBaseUrl;
    }

    public void setInternalBaseUrl(String internalBaseUrl) {
      this.internalBaseUrl = internalBaseUrl;
    }

    public String getJwtSecret() {
      return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
      this.jwtSecret = jwtSecret;
    }

    public String getDefaultUserId() {
      return defaultUserId;
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

    public void setDefaultUserId(String defaultUserId) {
      this.defaultUserId = defaultUserId;
    }

    public String getDefaultUserName() {
      return defaultUserName;
    }

    public void setDefaultUserName(String defaultUserName) {
      this.defaultUserName = defaultUserName;
    }
  }
}
