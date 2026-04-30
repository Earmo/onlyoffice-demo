package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtAccessContextProviderTest {

    @Test
    void shouldResolveContextFromBearerToken() {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
        properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");
        JwtAccessContextProvider provider = new JwtAccessContextProvider(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + signToken(properties, Map.of(
                "tenantId", "tenant-jwt",
                "sourceSystem", "crm",
                "externalUserId", "jwt-user",
                "displayName", "JWT Alice",
                "permissions", Map.of("edit", false, "download", true)
        )));

        Optional<AccessContext> accessContext = provider.resolve(request);

        assertTrue(accessContext.isPresent());
        assertEquals("tenant-jwt", accessContext.get().tenantId());
        assertEquals("crm", accessContext.get().sourceSystem());
        assertEquals("jwt-user", accessContext.get().externalUserId());
        assertEquals("JWT Alice", accessContext.get().displayName());
        assertEquals(false, accessContext.get().permission("edit", true));
        assertEquals(true, accessContext.get().permission("download", false));
        assertEquals("jwt", accessContext.get().source());
    }

    @Test
    void shouldRejectInvalidBearerToken() {
        JwtAccessContextProvider provider = new JwtAccessContextProvider(new OnlyofficeIntegrationProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-valid-token");

        InvalidAccessContextException exception = assertThrows(
                InvalidAccessContextException.class,
                () -> provider.resolve(request)
        );

        assertTrue(exception.getMessage().contains("访问上下文解析失败"));
    }

    private String signToken(OnlyofficeIntegrationProperties properties, Map<String, Object> claims) {
        SecretKey secretKey = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(claims)
                .signWith(secretKey)
                .compact();
    }
}
