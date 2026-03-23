package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析文档服务消费的标准化请求上下文。
 */
@Component
@RequiredArgsConstructor
public class RequestContextResolver {

  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String SOURCE_SYSTEM_HEADER = "X-Source-System";
  public static final String EXTERNAL_USER_ID_HEADER = "X-External-User-Id";
  public static final String DISPLAY_NAME_HEADER = "X-User-Display-Name";

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

  public RequestContext resolve(HttpServletRequest request) {
    if (request == null) {
      return defaultContext();
    }

    return new RequestContext(
        readHeaderOrDefault(request, TENANT_HEADER, onlyofficeIntegrationProperties.getDefaultTenantId()),
        readHeaderOrDefault(request, SOURCE_SYSTEM_HEADER, onlyofficeIntegrationProperties.getDefaultSourceSystem()),
        readHeaderOrDefault(request, EXTERNAL_USER_ID_HEADER, onlyofficeIntegrationProperties.getDefaultUser()),
        readHeaderOrDefault(request, DISPLAY_NAME_HEADER, onlyofficeIntegrationProperties.getDefaultUserName())
    );
  }

  private RequestContext defaultContext() {
    return new RequestContext(
        onlyofficeIntegrationProperties.getDefaultTenantId(),
        onlyofficeIntegrationProperties.getDefaultSourceSystem(),
        onlyofficeIntegrationProperties.getDefaultUser(),
        onlyofficeIntegrationProperties.getDefaultUserName()
    );
  }

  private String readHeaderOrDefault(HttpServletRequest request, String headerName, String defaultValue) {
    String value = request.getHeader(headerName);
    return StringUtils.hasText(value) ? value.trim() : defaultValue;
  }
}
