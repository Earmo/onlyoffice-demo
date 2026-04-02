package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderAccessContextProviderTest {

  @Test
  void shouldResolveContextFromStandardHeaders() {
    HeaderAccessContextProvider provider = new HeaderAccessContextProvider(new OnlyofficeIntegrationProperties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Tenant-Id", "tenant-a");
    request.addHeader("X-Source-System", "erp");
    request.addHeader("X-External-User-Id", "user-a");
    request.addHeader("X-User-Display-Name", "Alice");
    request.addHeader("X-Access-Permissions", "edit=false,download=true,comment=true,print=false");

    Optional<AccessContext> accessContext = provider.resolve(request);

    assertTrue(accessContext.isPresent());
    assertEquals("tenant-a", accessContext.get().tenantId());
    assertEquals("erp", accessContext.get().sourceSystem());
    assertEquals("user-a", accessContext.get().externalUserId());
    assertEquals("Alice", accessContext.get().displayName());
    assertFalse(accessContext.get().permission("edit", true));
    assertTrue(accessContext.get().permission("download", false));
    assertEquals("header", accessContext.get().source());
  }

  @Test
  void shouldReturnEmptyWhenNoHeaderExists() {
    HeaderAccessContextProvider provider = new HeaderAccessContextProvider(new OnlyofficeIntegrationProperties());

    assertTrue(provider.resolve(new MockHttpServletRequest()).isEmpty());
  }
}
