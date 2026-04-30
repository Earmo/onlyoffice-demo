package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 默认访问上下文 provider。
 *
 * <p>它的作用不是永久兜底，而是给 `AccessContextResolver` 提供一份标准默认值，
 * 用于在受控条件下补齐缺失字段，或者在显式关闭严格模式时作为开发默认上下文。
 */
@Component
@RequiredArgsConstructor
public class DefaultAccessContextProvider implements AccessContextProvider {

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public boolean isExplicitStrategy() {
        return false;
    }

    @Override
    public Optional<AccessContext> resolve(HttpServletRequest request) {
        return Optional.of(new AccessContext(
                onlyofficeIntegrationProperties.getDefaultTenantId(),
                onlyofficeIntegrationProperties.getDefaultSourceSystem(),
                onlyofficeIntegrationProperties.getDefaultUser(),
                onlyofficeIntegrationProperties.getDefaultUserName(),
                Map.of(),
                name()
        ));
    }
}
