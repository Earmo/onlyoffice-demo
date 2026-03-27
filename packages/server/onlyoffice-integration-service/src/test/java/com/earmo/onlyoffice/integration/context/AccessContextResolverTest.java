package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessContextResolverTest {

  @Test
  void shouldUseConfiguredResolutionOrder() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.getAccessContext().setResolutionOrder(List.of("custom-provider", "header", "jwt", "default"));
    properties.getAccessContext().setEnabledProviders(List.of("custom-provider", "header", "jwt", "default"));
    properties.getAccessContext().setAllowDefaultContext(true);

    AccessContextProvider customProvider = new StubAccessContextProvider(
        "custom-provider",
        new AccessContext("tenant-custom", "custom", "custom-user", "Custom User", java.util.Map.of(), "custom-provider")
    );
    AccessContextResolver resolver = new AccessContextResolver(
        properties,
        List.of(
            customProvider,
            new HeaderAccessContextProvider(properties),
            new JwtAccessContextProvider(properties),
            new DefaultAccessContextProvider(properties)
        )
    );

    AccessContext accessContext = resolver.resolve(new MockHttpServletRequest());

    assertEquals("custom-user", accessContext.externalUserId());
    assertIterableEquals(List.of("custom-provider", "header", "jwt", "default"), resolver.resolutionOrder());
  }

  @Test
  void shouldFillMissingFieldsFromDefaultContextWhenAllowed() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setDefaultTenantId("tenant-default");
    properties.setDefaultSourceSystem("native");
    properties.setDefaultUser("starter-user");
    properties.setDefaultUserName("默认用户");
    properties.getAccessContext().setAllowDefaultContext(true);

    AccessContextResolver resolver = new AccessContextResolver(
        properties,
        List.of(new HeaderAccessContextProvider(properties), new DefaultAccessContextProvider(properties))
    );
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-External-User-Id", "user-a");
    request.addHeader("X-User-Display-Name", "Alice");

    AccessContext accessContext = resolver.resolve(request);

    assertEquals("tenant-default", accessContext.tenantId());
    assertEquals("native", accessContext.sourceSystem());
    assertEquals("user-a", accessContext.externalUserId());
    assertEquals("Alice", accessContext.displayName());
  }

  @Test
  void shouldTreatDefaultProviderAsNonExplicitStrategy() {
    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();

    assertFalse(new DefaultAccessContextProvider(properties).isExplicitStrategy());
    assertTrue(new StubAccessContextProvider(
        "custom-provider",
        new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of(), "custom")
    ).isExplicitStrategy());
  }

  private record StubAccessContextProvider(String name, AccessContext accessContext) implements AccessContextProvider {

    @Override
    public java.util.Optional<AccessContext> resolve(jakarta.servlet.http.HttpServletRequest request) {
      return java.util.Optional.of(accessContext);
    }
  }
}
