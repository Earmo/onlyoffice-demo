package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.EditorConfigResponse;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 负责拼装 ONLYOFFICE 编辑器初始化配置。
 *
 * <p>前端真正需要的不是文档字节，而是一份符合 ONLYOFFICE 约定的 config。
 * 这份配置里同时包含文档元信息、回调地址、用户信息以及 JWT 签名。
 */
@Service
public class OnlyofficeConfigService {

  private final DemoProperties demoProperties;
  private final DocumentStorageService documentStorageService;
  private final OnlyofficeJwtService onlyofficeJwtService;

  public OnlyofficeConfigService(
      DemoProperties demoProperties,
      DocumentStorageService documentStorageService,
      OnlyofficeJwtService onlyofficeJwtService
  ) {
    this.demoProperties = demoProperties;
    this.documentStorageService = documentStorageService;
    this.onlyofficeJwtService = onlyofficeJwtService;
  }

  /**
   * 构造前端初始化编辑器所需的完整响应。
   *
   * <p>返回结果分成两层：
   * 1. documentServerUrl：前端用来定位 ONLYOFFICE Docs；
   * 2. config：前端原样传给 ONLYOFFICE Vue 组件。
   */
  public EditorConfigResponse buildEditorConfig(String documentId, boolean readonly) throws IOException {
    StoredDocument storedDocument = documentStorageService.getOrCreateDocument(documentId);

    Map<String, Object> config = new LinkedHashMap<>();
    config.put("documentType", storedDocument.documentType());
    config.put("type", "desktop");
    config.put("width", "100%");
    config.put("height", "100%");
    config.put("document", buildDocumentSection(storedDocument, readonly));
    config.put("editorConfig", buildEditorSection(storedDocument, readonly));
    config.put("token", onlyofficeJwtService.sign(config));

    return new EditorConfigResponse(
        demoProperties.getOnlyoffice().getDocumentServerUrl(),
        config
    );
  }

  /**
   * 生成 config.document 段。
   *
   * <p>这里主要告诉 ONLYOFFICE：
   * 当前文件叫什么、是什么类型、从哪里下载、有哪些权限。
   */
  private Map<String, Object> buildDocumentSection(StoredDocument storedDocument, boolean readonly) {
    Map<String, Object> permissions = new LinkedHashMap<>();
    permissions.put("edit", !readonly);
    permissions.put("download", true);
    // 关闭打印能力后，相关入口会被禁用或隐藏。
    permissions.put("print", false);
    permissions.put("review", false);
    permissions.put("fillForms", false);
    // 关闭评论能力，减少顶部和侧边栏相关 UI。
    permissions.put("comment", false);
    permissions.put("chat", false);

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("title", storedDocument.title());
    document.put("fileType", storedDocument.fileType());
    // key 是 ONLYOFFICE 用于区分文档版本的重要字段，这里用“文档 ID + 最后修改时间”构造。
    document.put("key", storedDocument.documentId() + "-" + storedDocument.lastModified().toEpochMilli());
    document.put("url", buildInternalUrl("/api/documents/%s/file".formatted(storedDocument.documentId())));
    document.put("permissions", permissions);
    return document;
  }

  /**
   * 生成 config.editorConfig 段。
   *
   * <p>这里定义编辑模式、保存回调地址，以及展示给编辑器的当前用户信息。
   */
  private Map<String, Object> buildEditorSection(StoredDocument storedDocument, boolean readonly) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", demoProperties.getOnlyoffice().getDefaultUserId());
    user.put("name", demoProperties.getOnlyoffice().getDefaultUserName());

    Map<String, Object> header = new LinkedHashMap<>();
    // 以下 layout.* 多数属于白标能力；Community 版通常会忽略不支持的字段。
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
    viewToolbar.put("navigation", false);

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
    leftMenu.put("mode", false);
    leftMenu.put("navigation", false);
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
    // lang 控制编辑器界面语言；简体中文使用 zh。
    editorConfig.put("lang", demoProperties.getOnlyoffice().getDefaultLanguage());
    // region 主要影响日期、时间、货币等本地化格式，尤其是表格编辑器。
    editorConfig.put("region", demoProperties.getOnlyoffice().getDefaultRegion());
    // 只读切换通过重新初始化编辑器完成，因此这里直接输出最终 mode。
    editorConfig.put("mode", readonly ? "view" : "edit");
    editorConfig.put("callbackUrl", buildInternalUrl("/api/documents/%s/callback".formatted(storedDocument.documentId())));
    editorConfig.put("user", user);
    editorConfig.put("customization", customization);
    return editorConfig;
  }

  /**
   * 基于内部访问地址拼出 ONLYOFFICE 容器实际会调用的完整 URL。
   */
  private String buildInternalUrl(String path) {
    return UriComponentsBuilder.fromHttpUrl(demoProperties.getOnlyoffice().getInternalBaseUrl())
        .path(path)
        .build()
        .toUriString();
  }

}
