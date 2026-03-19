package com.earmo.onlyoffice.demo.web;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestContextResolverTest {

  @Test
  void shouldResolveHeadersWithFallbackDefaults() {
    DemoProperties properties = new DemoProperties();
    properties.setDefaultTenantId("native");
    properties.setDefaultSourceSystem("native");
    properties.setDefaultUserId("demo-user");
    properties.setDefaultUserName("演示用户");

    RequestContextResolver resolver = new RequestContextResolver(properties);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestContextResolver.TENANT_HEADER, "tenant-a");
    request.addHeader(RequestContextResolver.SOURCE_SYSTEM_HEADER, "erp");
    request.addHeader(RequestContextResolver.EXTERNAL_USER_ID_HEADER, "user-a");
    request.addHeader(RequestContextResolver.DISPLAY_NAME_HEADER, "Alice");

    RequestContext context = resolver.resolve(request);

    assertEquals("tenant-a", context.tenantId());
    assertEquals("erp", context.sourceSystem());
    assertEquals("user-a", context.externalUserId());
    assertEquals("Alice", context.displayName());
  }

  @Test
  void shouldFallbackToConfiguredDefaultsWhenHeadersMissing() {
    DemoProperties properties = new DemoProperties();
    properties.setDefaultTenantId("tenant-default");
    properties.setDefaultSourceSystem("native");
    properties.setDefaultUserId("demo-user");
    properties.setDefaultUserName("演示用户");

    RequestContextResolver resolver = new RequestContextResolver(properties);
    RequestContext context = resolver.resolve(new MockHttpServletRequest());

    assertEquals("tenant-default", context.tenantId());
    assertEquals("native", context.sourceSystem());
    assertEquals("demo-user", context.externalUserId());
    assertEquals("演示用户", context.displayName());
  }
}
