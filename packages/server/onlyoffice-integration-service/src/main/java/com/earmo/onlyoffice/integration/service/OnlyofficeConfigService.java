package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 负责拼装 ONLYOFFICE 编辑器初始化配置。
 */
@Service
@RequiredArgsConstructor
public class OnlyofficeConfigService {

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  private final DocumentStorageService documentStorageService;
  private final OnlyofficeJwtService onlyofficeJwtService;

  public EditorConfigResponse buildEditorConfig(
      String documentId,
      boolean readonly,
      AccessContext accessContext,
      jakarta.servlet.http.HttpServletRequest request
  ) throws IOException {
    StoredDocument storedDocument = documentStorageService.getRequiredDocument(documentId);

    Map<String, Object> config = new LinkedHashMap<>();
    config.put("documentType", storedDocument.documentType());
    config.put("type", "desktop");
    config.put("width", "100%");
    config.put("height", "100%");
    config.put("document", buildDocumentSection(storedDocument, readonly, accessContext));
    config.put("editorConfig", buildEditorSection(storedDocument, readonly, accessContext));
    config.put("token", onlyofficeJwtService.sign(config));

    return new EditorConfigResponse(resolveDocumentServerUrl(), config);
  }

  private Map<String, Object> buildDocumentSection(
      StoredDocument storedDocument,
      boolean readonly,
      AccessContext accessContext
  ) {
    boolean canEdit = !readonly && accessContext.permission("edit", true);
    Map<String, Object> permissions = new LinkedHashMap<>();
    permissions.put("edit", canEdit);
    permissions.put("download", accessContext.permission("download", true));
    permissions.put("print", accessContext.permission("print", false));
    permissions.put("review", false);
    permissions.put("fillForms", false);
    permissions.put("comment", accessContext.permission("comment", false));
    permissions.put("chat", false);

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("title", storedDocument.title());
    document.put("fileType", storedDocument.fileType());
    document.put("key", storedDocument.documentId() + "-" + storedDocument.lastModified().toEpochMilli());
    document.put("url", buildInternalUrl("/api/documents/%s/file".formatted(storedDocument.documentId())));
    document.put("permissions", permissions);
    return document;
  }

  private Map<String, Object> buildEditorSection(
      StoredDocument storedDocument,
      boolean readonly,
      AccessContext accessContext
  ) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", accessContext.externalUserId());
    user.put("name", accessContext.displayName());

    Map<String, Object> header = new LinkedHashMap<>();
    header.put("editMode", false);
    header.put("save", false);
    header.put("user", false);
    header.put("users", false);

    Map<String, Object> fileToolbar = new LinkedHashMap<>();
    fileToolbar.put("close", false);
    fileToolbar.put("info", false);
    fileToolbar.put("save", false);
    fileToolbar.put("settings", false);

    Map<String, Object> viewToolbar = new LinkedHashMap<>();
    viewToolbar.put("navigation", true);

    Map<String, Object> toolbar = new LinkedHashMap<>();
    toolbar.put("file", fileToolbar);
    toolbar.put("save", false);
    toolbar.put("view", viewToolbar);
    toolbar.put("plugins", false);
    toolbar.put("draw", false);
    toolbar.put("layout", false);
    toolbar.put("references", false);
    toolbar.put("protect", false);
    toolbar.put("collaboration", false);

    Map<String, Object> leftMenu = new LinkedHashMap<>();
    leftMenu.put("mode", true);
    leftMenu.put("navigation", true);
    leftMenu.put("spellcheck", false);

    Map<String, Object> rightMenu = new LinkedHashMap<>();
    rightMenu.put("mode", false);

    Map<String, Object> statusBar = new LinkedHashMap<>();
    statusBar.put("actionStatus", false);
    statusBar.put("textLang", false);
    statusBar.put("words", false);
    statusBar.put("zoom", false);

    Map<String, Object> layout = new LinkedHashMap<>();
    layout.put("header", header);
    layout.put("toolbar", toolbar);
    layout.put("leftMenu", leftMenu);
    layout.put("rightMenu", rightMenu);
    layout.put("statusBar", statusBar);

    Map<String, Object> logo = new LinkedHashMap<>();
    logo.put("visible", false);
    logo.put("url", "https://avatars.githubusercontent.com/u/42935502?v=4&size=64");

    Map<String, Object> customization = new LinkedHashMap<>();
    customization.put("autosave", false);
    customization.put("forcesave", false);
    customization.put("comments", false);
    customization.put("compactHeader", true);
    customization.put("compactToolbar", true);
    customization.put("feedback", Map.of("visible", false));
    customization.put("features", Map.of("featuresTips", false));
    customization.put("help", false);
    customization.put("hideRightMenu", true);
    customization.put("hideRulers", true);
    customization.put("integrationMode", "embed");
    customization.put("layout", layout);
    customization.put("logo", logo);
    customization.put("close", Map.of("visible", false));
    customization.put("suggestFeature", false);
    customization.put("toolbarHideFileName", true);

    Map<String, Object> editorConfig = new LinkedHashMap<>();
    editorConfig.put("lang", onlyofficeIntegrationProperties.getDefaultLanguage());
    editorConfig.put("region", onlyofficeIntegrationProperties.getDefaultRegion());
    editorConfig.put("mode", readonly || !accessContext.permission("edit", true) ? "view" : "edit");
    editorConfig.put("callbackUrl", buildInternalUrl("/api/documents/%s/callback".formatted(storedDocument.documentId())));
    editorConfig.put("user", user);
    editorConfig.put("customization", customization);
    return editorConfig;
  }

  private String buildInternalUrl(String path) {
    return UriComponentsBuilder.fromHttpUrl(
            requireConfiguredBaseUrl(
                onlyofficeIntegrationProperties.getInternalBaseUrl(),
                "onlyoffice.integration.internal-base-url"
            )
        )
        .path(path)
        .build()
        .toUriString();
  }

  private String resolveDocumentServerUrl() {
    if (StringUtils.hasText(onlyofficeIntegrationProperties.getDocumentServerUrl())) {
      return ensureTrailingSlash(requireConfiguredBaseUrl(
          onlyofficeIntegrationProperties.getDocumentServerUrl(),
          "onlyoffice.integration.document-server-url"
      ));
    }

    if (StringUtils.hasText(onlyofficeIntegrationProperties.getPublicBaseUrl())) {
      return ensureTrailingSlash(requireConfiguredBaseUrl(
          onlyofficeIntegrationProperties.getPublicBaseUrl(),
          "onlyoffice.integration.public-base-url"
      ));
    }

    throw new IllegalStateException(
        "ONLYOFFICE 运行配置缺失：请配置 onlyoffice.integration.document-server-url，"
            + "或至少提供 onlyoffice.integration.public-base-url。"
    );
  }

  private String ensureTrailingSlash(String url) {
    return url.endsWith("/") ? url : url + "/";
  }

  /**
   * Phase 5 开始，运行时地址不再从请求动态猜测，而是要求通过显式配置给出稳定角色地址。
   * 这样部署为独立服务、网关聚合服务或多实例服务时，生成出来的 URL 语义才是可预期的。
   */
  private String requireConfiguredBaseUrl(String rawUrl, String propertyName) {
    if (!StringUtils.hasText(rawUrl)) {
      throw new IllegalStateException("ONLYOFFICE 运行配置缺失：" + propertyName + " 不能为空。");
    }

    try {
      URI uri = new URI(rawUrl.trim());
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
        throw new IllegalStateException(
            "ONLYOFFICE 运行配置非法：" + propertyName + " 必须是完整的 http/https 地址。"
        );
      }
      String normalizedScheme = scheme.toLowerCase();
      if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
        throw new IllegalStateException(
            "ONLYOFFICE 运行配置非法：" + propertyName + " 只支持 http/https 地址。"
        );
      }
      return rawUrl.trim();
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(
          "ONLYOFFICE 运行配置非法：" + propertyName + " 不是合法 URL。",
          ex
      );
    }
  }
}


