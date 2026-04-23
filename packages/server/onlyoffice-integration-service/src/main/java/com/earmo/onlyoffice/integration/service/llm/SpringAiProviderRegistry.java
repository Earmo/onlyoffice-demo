package com.earmo.onlyoffice.integration.service.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Spring AI provider 注册表。
 *
 * <p>启动时把所有 {@link SpringAiLlmProvider} 按 `providerName` 建立索引，
 * 供领域服务按配置中的实现名查找具体 provider。
 */
@Component
public class SpringAiProviderRegistry {

  private final Map<String, SpringAiLlmProvider> providers;

  /**
   * 构建 provider 名称到实现的只读映射。
   */
  public SpringAiProviderRegistry(List<SpringAiLlmProvider> providers) {
    LinkedHashMap<String, SpringAiLlmProvider> registry = new LinkedHashMap<>();
    for (SpringAiLlmProvider provider : providers) {
      registry.put(provider.providerName(), provider);
    }
    this.providers = Map.copyOf(registry);
  }

  /**
   * 按实现名查找 provider。
   */
  public Optional<SpringAiLlmProvider> findProvider(String providerName) {
    if (providerName == null || providerName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(providers.get(providerName.trim()));
  }
}
