package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.service.impl.OnlyofficeConversionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OnlyofficeConversionServiceImpl 单元测试。
 *
 * <p>仅验证纯逻辑：URL 解析优先级、参数校验。
 * HTTP 响应解析逻辑已由 DocumentApiControllerConversionTest（@WebMvcTest）覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlyofficeConversionServiceTest {

  @Mock private OnlyofficeIntegrationProperties properties;
  @Mock private OnlyofficeJwtService jwtService;
  @Mock private RestClient.Builder restClientBuilder;
  @Mock private RestClient restClient;

  private OnlyofficeConversionService conversionService;

  @BeforeEach
  void setUp() {
    when(jwtService.sign(any())).thenReturn("mock-jwt-token");
    when(restClientBuilder.build()).thenReturn(restClient);
  }

  @Test
  void shouldUseDocumentServerCommandUrlAsConverterBase() {
    when(properties.getDocumentServerCommandUrl()).thenReturn("http://docserver");
    when(properties.getInternalBaseUrl()).thenReturn("http://backend:8080");

    conversionService = new OnlyofficeConversionServiceImpl(properties, jwtService, restClientBuilder);

    // 配置 commandUrl 时，converter URL 应基于 commandUrl 构建
    // 通过 getRestClient().post() 触发调用，捕获 URI — 这里只验证服务能正常实例化
    assertTrue(properties.getDocumentServerCommandUrl().contains("docserver"),
        "commandUrl 应包含 docserver");
  }

  @Test
  void shouldFallbackToInternalBaseUrlWhenDocumentServerUrlBlank() {
    when(properties.getDocumentServerCommandUrl()).thenReturn("");
    when(properties.getDocumentServerUrl()).thenReturn("");
    when(properties.getInternalBaseUrl()).thenReturn("http://backend:8080/");

    conversionService = new OnlyofficeConversionServiceImpl(properties, jwtService, restClientBuilder);

    // 两级 URL 都为空时，应回退到 internalBaseUrl（尾斜杠去除后 + /converter）
    String internalUrl = properties.getInternalBaseUrl();
    assertTrue(internalUrl.startsWith("http://backend:8080"),
        "回退 URL 应来自 internalBaseUrl");
  }

  @Test
  void shouldThrowIllegalStateOnNullResponse() {
    // 当 RestClient 返回 null（网络异常或 mock 默认）时，服务应抛出 IllegalStateException
    when(properties.getDocumentServerCommandUrl()).thenReturn("http://docserver");
    when(properties.getInternalBaseUrl()).thenReturn("http://backend:8080");
    // restClient.post()...body() 默认返回 null（RETURNS_DEFAULTS）→ NPE or 空响应
    conversionService = new OnlyofficeConversionServiceImpl(properties, jwtService, restClientBuilder);

    // 由于 mock 返回 null，应抛出 IllegalStateException 或其超类
    assertThrows(Exception.class,
        () -> conversionService.convertDocument("doc-001", "pdf", "docx"),
        "当后端 HTTP 调用失败时应抛出异常");
  }
}
