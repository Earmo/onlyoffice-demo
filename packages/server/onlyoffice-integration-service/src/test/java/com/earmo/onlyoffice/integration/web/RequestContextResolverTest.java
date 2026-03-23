package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestContextResolverTest {

  @Test
  void shouldResolveHeadersWithFallbackDefaults() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setDefaultTenantId("native");
    properties.setDefaultSourceSystem("native");
    properties.setDefaultUser("starter-user");
    properties.setDefaultUserName("默认用户");

    RequestContextResolver resolver = new RequestContextResolver(properties);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestContextResolver.TENANT_HEADER, "tenant-a");
    request.addHeader(RequestContextResolver.SOURCE_SYSTEM_HEADER, "erp");
    request.addHeader(RequestContextResolver.EXTERNAL_USER_ID_HEADER, "user-a");
    request.addHeader(RequestContextResolver.DISPLAY_NAME_HEADER, "Alice");

    RequestContext context = resolver.resolve(request);

    assertEquals("tenant-a", context.tenantId());
    assertEquals("erp", context.sourceSystem());
    assertEquals("user-a", context.externalUser());
    assertEquals("Alice", context.displayName());
  }

  @Test
  void shouldFallbackToConfiguredDefaultsWhenHeadersMissing() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setDefaultTenantId("tenant-default");
    properties.setDefaultSourceSystem("native");
    properties.setDefaultUser("starter-user");
    properties.setDefaultUserName("默认用户");

    RequestContextResolver resolver = new RequestContextResolver(properties);
    RequestContext context = resolver.resolve(new MockHttpServletRequest());

    assertEquals("tenant-default", context.tenantId());
    assertEquals("native", context.sourceSystem());
    assertEquals("starter-user", context.externalUser());
    assertEquals("默认用户", context.displayName());
  }
}
