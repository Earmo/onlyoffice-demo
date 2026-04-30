package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class AccessContextPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "onlyoffice.integration.access-context.enabled-providers=header,jwt,default",
                    "onlyoffice.integration.access-context.resolution-order=jwt,header,default",
                    "onlyoffice.integration.access-context.require-explicit-context=true",
                    "onlyoffice.integration.access-context.allow-default-context=true",
                    "onlyoffice.integration.access-context.header.enabled=true",
                    "onlyoffice.integration.access-context.header.permissions-header=X-User-Permissions",
                    "onlyoffice.integration.access-context.jwt.enabled=true",
                    "onlyoffice.integration.access-context.jwt.header-name=X-Access-Token",
                    "onlyoffice.integration.access-context.jwt.claim-mappings.tenant-id=tenant",
                    "onlyoffice.integration.access-context.jwt.claim-mappings.source-system=source",
                    "onlyoffice.integration.access-context.jwt.claim-mappings.external-user-id=userId",
                    "onlyoffice.integration.access-context.jwt.claim-mappings.display-name=nickname",
                    "onlyoffice.integration.access-context.jwt.claim-mappings.permissions=permissions"
            );

    @Test
    void shouldBindAccessContextProperties() {
        contextRunner.run(context -> {
            OnlyofficeIntegrationProperties properties = context.getBean(OnlyofficeIntegrationProperties.class);

            assertIterableEquals(
                    java.util.List.of("header", "jwt", "default"),
                    properties.getAccessContext().getEnabledProviders()
            );
            assertIterableEquals(
                    java.util.List.of("jwt", "header", "default"),
                    properties.getAccessContext().getResolutionOrder()
            );
            assertTrue(properties.getAccessContext().isRequireExplicitContext());
            assertTrue(properties.getAccessContext().isAllowDefaultContext());
            assertTrue(properties.getAccessContext().getHeader().isEnabled());
            assertEquals("X-User-Permissions", properties.getAccessContext().getHeader().getPermissionsHeader());
            assertTrue(properties.getAccessContext().getJwt().isEnabled());
            assertEquals("X-Access-Token", properties.getAccessContext().getJwt().getHeaderName());
            assertEquals("tenant", properties.getAccessContext().getJwt().getClaimMappings().getTenantId());
            assertEquals("source", properties.getAccessContext().getJwt().getClaimMappings().getSourceSystem());
            assertEquals("userId", properties.getAccessContext().getJwt().getClaimMappings().getExternalUserId());
            assertEquals("nickname", properties.getAccessContext().getJwt().getClaimMappings().getDisplayName());
            assertEquals("permissions", properties.getAccessContext().getJwt().getClaimMappings().getPermissions());
        });
    }

    @Test
    void shouldKeepStrictDefaultsWhenNoOverrideProvided() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    OnlyofficeIntegrationProperties properties = context.getBean(OnlyofficeIntegrationProperties.class);
                    assertTrue(properties.getAccessContext().isRequireExplicitContext());
                    assertFalse(properties.getAccessContext().isAllowDefaultContext());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OnlyofficeIntegrationProperties.class)
    static class TestConfig {
    }
}
