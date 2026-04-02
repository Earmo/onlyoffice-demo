package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从标准请求头解析访问上下文。
 *
 * <p>这里继续兼容历史上已经在用的几个头：
 * `X-Tenant-Id`、`X-Source-System`、`X-External-User-Id`、`X-User-Display-Name`。
 * 但它不再是系统里唯一的身份来源，而只是内置 provider 之一。
 */
@Component
@RequiredArgsConstructor
public class HeaderAccessContextProvider implements AccessContextProvider {

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

  @Override
  public String name() {
    return "header";
  }

  @Override
  public Optional<AccessContext> resolve(HttpServletRequest request) {
    if (request == null || !onlyofficeIntegrationProperties.getAccessContext().getHeader().isEnabled()) {
      return Optional.empty();
    }

    OnlyofficeIntegrationProperties.HeaderAccessContextProperties headerProperties =
        onlyofficeIntegrationProperties.getAccessContext().getHeader();

    String tenantId = readHeader(request, headerProperties.getTenantIdHeader());
    String sourceSystem = readHeader(request, headerProperties.getSourceSystemHeader());
    String externalUserId = readHeader(request, headerProperties.getExternalUserIdHeader());
    String displayName = readHeader(request, headerProperties.getDisplayNameHeader());
    String permissionsHeader = readHeader(request, headerProperties.getPermissionsHeader());

    AccessContext accessContext = new AccessContext(
        tenantId,
        sourceSystem,
        externalUserId,
        displayName,
        AccessContextPermissionParser.parse(permissionsHeader),
        name()
    );
    return accessContext.hasAnyExplicitValue() ? Optional.of(accessContext) : Optional.empty();
  }

  private String readHeader(HttpServletRequest request, String headerName) {
    String value = request.getHeader(headerName);
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
