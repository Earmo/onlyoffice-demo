package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 兼容旧调用链的 `RequestContext` 包装器。
 *
 * <p>Phase 3 之后，真正的解析入口已经切到 `AccessContextResolver`。
 * 这里保留旧类名，是为了让尚未迁移完的服务层还能继续接收 `RequestContext`，
 * 同时避免上层代码绕过新的 provider SPI。
 */
@Component
@RequiredArgsConstructor
public class RequestContextResolver {

  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String SOURCE_SYSTEM_HEADER = "X-Source-System";
  public static final String EXTERNAL_USER_ID_HEADER = "X-External-User-Id";
  public static final String DISPLAY_NAME_HEADER = "X-User-Display-Name";

  private final AccessContextResolver accessContextResolver;

  public RequestContext resolve(HttpServletRequest request) {
    return accessContextResolver.resolve(request).toRequestContext();
  }
}
