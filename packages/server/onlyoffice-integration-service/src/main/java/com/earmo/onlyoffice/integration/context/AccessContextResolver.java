package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按配置顺序聚合访问上下文策略。
 *
 * <p>这里把“缺少用户上下文”“部分字段缺失”“访问上下文解析失败”三种语义统一收口：
 * 1. 先按 resolutionOrder 尝试所有显式策略；
 * 2. 如果命中了部分字段，再按 allowDefaultContext 决定是否使用默认值补齐；
 * 3. 如果完全没有显式上下文，则按 requireExplicitContext / allowDefaultContext 决定返回 4xx 还是允许回退默认补齐策略。
 */
@Component
@RequiredArgsConstructor
public class AccessContextResolver {

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  private final List<AccessContextProvider> accessContextProviders;

  public AccessContext resolve(HttpServletRequest request) {
    Map<String, AccessContextProvider> providersByName = providersByName();
    LinkedHashSet<String> providerNames = orderedProviderNames();

    AccessContext partialContext = null;
    for (String providerName : providerNames) {
      AccessContextProvider provider = providersByName.get(providerName);
      if (provider == null || !provider.isExplicitStrategy()) {
        continue;
      }

      AccessContext resolved = provider.resolve(request).orElse(null);
      if (resolved == null || !resolved.hasAnyExplicitValue()) {
        continue;
      }

      if (resolved.isComplete()) {
        return resolved;
      }
      if (partialContext == null) {
        partialContext = resolved;
      }
    }

    if (partialContext != null) {
      if (onlyofficeIntegrationProperties.getAccessContext().isAllowDefaultContext()) {
        return partialContext.fillMissing(resolveDefaultContext(providersByName, request));
      }
      throw new MissingAccessContextException("缺少用户上下文：tenantId、sourceSystem、externalUserId 或 displayName 不完整。");
    }

    if (!onlyofficeIntegrationProperties.getAccessContext().isRequireExplicitContext()
        && onlyofficeIntegrationProperties.getAccessContext().isAllowDefaultContext()) {
      return resolveDefaultContext(providersByName, request);
    }

    throw new MissingAccessContextException("缺少用户上下文：请求中未提供有效的访问上下文。");
  }

  /**
   * 对外暴露顺序信息，便于测试校验解析链和自定义 provider 覆盖行为。
   */
  public List<String> resolutionOrder() {
    return List.copyOf(orderedProviderNames());
  }

  private AccessContext resolveDefaultContext(
      Map<String, AccessContextProvider> providersByName,
      HttpServletRequest request
  ) {
    AccessContextProvider defaultProvider = providersByName.get("default");
    if (defaultProvider == null || defaultProvider.isExplicitStrategy()) {
      throw new MissingAccessContextException("缺少用户上下文：未配置 default provider，无法补齐默认值。");
    }
    return defaultProvider.resolve(request)
        .orElseThrow(() -> new MissingAccessContextException("缺少用户上下文：default provider 未返回默认上下文。"));
  }

  private LinkedHashSet<String> orderedProviderNames() {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    names.addAll(onlyofficeIntegrationProperties.getAccessContext().getResolutionOrder());
    if (names.isEmpty()) {
      names.addAll(onlyofficeIntegrationProperties.getAccessContext().getEnabledProviders());
    }
    names.retainAll(onlyofficeIntegrationProperties.getAccessContext().getEnabledProviders());
    return names;
  }

  private Map<String, AccessContextProvider> providersByName() {
    Map<String, AccessContextProvider> providers = new LinkedHashMap<>();
    for (AccessContextProvider provider : accessContextProviders) {
      providers.put(provider.name(), provider);
    }
    return providers;
  }
}
