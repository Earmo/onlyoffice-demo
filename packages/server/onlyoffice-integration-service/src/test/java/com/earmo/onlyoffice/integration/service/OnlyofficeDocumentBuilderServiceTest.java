package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.service.impl.OnlyofficeDocumentBuilderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OnlyofficeDocumentBuilderServiceImpl 单元测试。
 *
 * <p>重点验证：
 * <ul>
 *   <li>脚本生成纯逻辑（无 HTTP 依赖）</li>
 *   <li>URL 解析优先级配置</li>
 *   <li>HTTP 失败时异常传播</li>
 * </ul>
 *
 * <p>HTTP 响应解析逻辑（error code / end=false 分支）
 * 已由 DocumentApiControllerWatermarkTest（@WebMvcTest）覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlyofficeDocumentBuilderServiceTest {

  @Mock private OnlyofficeIntegrationProperties properties;
  @Mock private OnlyofficeJwtService jwtService;
  @Mock private RestClient.Builder restClientBuilder;
  @Mock private RestClient restClient;

  private OnlyofficeDocumentBuilderService builderService;

  @BeforeEach
  void setUp() {
    when(properties.getDocumentServerCommandUrl()).thenReturn("http://docserver");
    when(properties.getInternalBaseUrl()).thenReturn("http://backend:8080");
    when(jwtService.sign(any())).thenReturn("mock-jwt-token");
    when(restClientBuilder.build()).thenReturn(restClient);

    builderService = new OnlyofficeDocumentBuilderServiceImpl(properties, jwtService, restClientBuilder);
  }

  // ─── 脚本生成（纯逻辑，无 HTTP）──────────────────────────────────────

  @Test
  void generateScriptShouldContainBuilderOpenFile() {
    String script = builderService.generateRemoveWatermarkScript("doc-001", "docx");

    assertNotNull(script);
    assertTrue(script.contains("builder.OpenFile"), "脚本应包含 builder.OpenFile");
    assertTrue(script.contains("RemoveWatermark"), "脚本应包含 RemoveWatermark()");
    assertTrue(script.contains("builder.SaveFile"), "脚本应包含 builder.SaveFile");
    assertTrue(script.contains("builder.CloseFile"), "脚本应包含 builder.CloseFile");
    assertTrue(script.contains("doc-001"), "脚本应包含 documentId");
    assertTrue(script.contains("docx"), "脚本应包含 fileType");
    assertTrue(script.contains("output.docx"), "脚本应包含输出文件名 output.docx");
  }

  @Test
  void generateScriptForOdtShouldContainOdtFileType() {
    String script = builderService.generateRemoveWatermarkScript("doc-002", "odt");

    assertTrue(script.contains("doc-002"), "脚本应包含 documentId");
    assertTrue(script.contains("odt"), "脚本应包含 fileType odt");
    assertTrue(script.contains("output.odt"), "脚本输出文件名应为 output.odt");
  }

  @Test
  void generateScriptShouldUseInternalBaseUrlForDocumentAccess() {
    when(properties.getInternalBaseUrl()).thenReturn("http://internal-backend:9080/");
    // 重新创建以使用新属性
    builderService = new OnlyofficeDocumentBuilderServiceImpl(properties, jwtService, restClientBuilder);

    String script = builderService.generateRemoveWatermarkScript("xyz-doc", "docx");

    assertTrue(script.contains("http://internal-backend:9080/api/documents/xyz-doc/file.docx"),
        "脚本中的文档 URL 应来自 internalBaseUrl（尾斜杠已处理）");
  }

  // ─── HTTP 失败传播 ────────────────────────────────────────────────────

  @Test
  void shouldThrowWhenHttpCallFails() {
    // restClient mock 默认返回 null，此时应抛出 IllegalStateException（空响应）
    assertThrows(Exception.class,
        () -> builderService.runScript("http://backend/script.js", "output.docx"),
        "当 HTTP 调用失败时应抛出异常");
  }
}
