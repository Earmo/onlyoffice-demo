package com.earmo.onlyoffice.demo.web;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析文档服务消费的标准化请求上下文。
 */
@Component
public class RequestContextResolver {

  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String SOURCE_SYSTEM_HEADER = "X-Source-System";
  public static final String EXTERNAL_USER_ID_HEADER = "X-External-User-Id";
  public static final String DISPLAY_NAME_HEADER = "X-User-Display-Name";

  private final DemoProperties demoProperties;

  public RequestContextResolver(DemoProperties demoProperties) {
    this.demoProperties = demoProperties;
  }

  public RequestContext resolve(HttpServletRequest request) {
    if (request == null) {
      return defaultContext();
    }

    return new RequestContext(
        readHeaderOrDefault(request, TENANT_HEADER, demoProperties.getDefaultTenantId()),
        readHeaderOrDefault(request, SOURCE_SYSTEM_HEADER, demoProperties.getDefaultSourceSystem()),
        readHeaderOrDefault(request, EXTERNAL_USER_ID_HEADER, demoProperties.getDefaultUserId()),
        readHeaderOrDefault(request, DISPLAY_NAME_HEADER, demoProperties.getDefaultUserName())
    );
  }

  private RequestContext defaultContext() {
    return new RequestContext(
        demoProperties.getDefaultTenantId(),
        demoProperties.getDefaultSourceSystem(),
        demoProperties.getDefaultUserId(),
        demoProperties.getDefaultUserName()
    );
  }

  private String readHeaderOrDefault(HttpServletRequest request, String headerName, String defaultValue) {
    String value = request.getHeader(headerName);
    return StringUtils.hasText(value) ? value.trim() : defaultValue;
  }
}
