package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomAccessContextProviderOverrideTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class)
      .withPropertyValues(
          "onlyoffice.integration.access-context.enabled-providers=custom-provider,header,default",
          "onlyoffice.integration.access-context.resolution-order=custom-provider,header,default",
          "onlyoffice.integration.access-context.require-explicit-context=true",
          "onlyoffice.integration.access-context.allow-default-context=true"
      );

  @Test
  void shouldAllowCustomProviderToOverrideBuiltIns() {
    contextRunner.run(context -> {
      AccessContextResolver resolver = context.getBean(AccessContextResolver.class);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Tenant-Id", "tenant-header");
      request.addHeader("X-Source-System", "native");
      request.addHeader("X-External-User-Id", "header-user");
      request.addHeader("X-User-Display-Name", "Header User");

      AccessContext accessContext = resolver.resolve(request);

      assertEquals("custom-user", accessContext.externalUserId());
      assertEquals(List.of("custom-provider", "header", "default"), resolver.resolutionOrder());
    });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OnlyofficeIntegrationProperties.class)
  static class TestConfig {

    @Bean
    AccessContextResolver accessContextResolver(
        OnlyofficeIntegrationProperties properties,
        List<AccessContextProvider> providers
    ) {
      return new AccessContextResolver(properties, providers);
    }

    @Bean
    HeaderAccessContextProvider headerAccessContextProvider(OnlyofficeIntegrationProperties properties) {
      return new HeaderAccessContextProvider(properties);
    }

    @Bean
    DefaultAccessContextProvider defaultAccessContextProvider(OnlyofficeIntegrationProperties properties) {
      return new DefaultAccessContextProvider(properties);
    }

    @Bean
    AccessContextProvider customProvider() {
      return new AccessContextProvider() {
        @Override
        public String name() {
          return "custom-provider";
        }

        @Override
        public java.util.Optional<AccessContext> resolve(jakarta.servlet.http.HttpServletRequest request) {
          return java.util.Optional.of(new AccessContext(
              "tenant-custom",
              "custom-system",
              "custom-user",
              "Custom Provider",
              java.util.Map.of("edit", true),
              "custom-provider"
          ));
        }
      };
    }
  }
}
