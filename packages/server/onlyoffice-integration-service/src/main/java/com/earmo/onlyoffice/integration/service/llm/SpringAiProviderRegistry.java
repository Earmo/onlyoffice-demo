package com.earmo.onlyoffice.integration.service.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SpringAiProviderRegistry {

  private final Map<String, SpringAiLlmProvider> providers;

  public SpringAiProviderRegistry(List<SpringAiLlmProvider> providers) {
    LinkedHashMap<String, SpringAiLlmProvider> registry = new LinkedHashMap<>();
    for (SpringAiLlmProvider provider : providers) {
      registry.put(provider.providerName(), provider);
    }
    this.providers = Map.copyOf(registry);
  }

  public Optional<SpringAiLlmProvider> findProvider(String providerName) {
    if (providerName == null || providerName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(providers.get(providerName.trim()));
  }
}
