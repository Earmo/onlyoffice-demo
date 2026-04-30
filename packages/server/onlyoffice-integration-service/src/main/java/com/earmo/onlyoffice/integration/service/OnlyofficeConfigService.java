package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.CurrentAccessContext;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import java.io.IOException;

/**
 * ONLYOFFICE editor-config 组装服务契约。
 *
 * <p>接口层只表达“给定文档与访问上下文后，生成可直接给前端消费的 editor-config”，
 * 具体的地址角色划分、权限映射和签名细节由默认实现负责。
 */
public interface OnlyofficeConfigService {

  default EditorConfigResponse buildEditorConfig(
      String documentId,
      boolean readonly,
      jakarta.servlet.http.HttpServletRequest request
  ) throws IOException {
    return buildEditorConfig(documentId, readonly, CurrentAccessContext.getRequired(), request);
  }

  EditorConfigResponse buildEditorConfig(
      String documentId,
      boolean readonly,
      AccessContext accessContext,
      jakarta.servlet.http.HttpServletRequest request
  ) throws IOException;
}
