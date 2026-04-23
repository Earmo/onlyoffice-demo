package com.earmo.onlyoffice.integration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 大模型对话链路配置根。
 */
@Validated
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmProperties {

  private boolean enabled = false;

  private boolean featureEnabled = true;

  private String defaultProvider = "dashscope";

  private String defaultModel = "";

  private long timeoutMillis = 60000L;

  private long requestSyncWaitMillis = 1500L;

  private int defaultVisibleTurns = 6;

  private int historyBudgetTokens = 12000;

  @NotBlank
  private String historyTokenEstimator = "chars_div_4";

  private boolean allowHeadingContext = true;

  @NotBlank
  private String defaultSystemPrompt = "你是一个文档协作助手。请基于用户给出的选区和标题上下文，输出可直接写回文档的专业中文建议。";

  private List<String> providerResponseMetaAllowlist = new ArrayList<>(List.of(
      "provider",
      "model",
      "created",
      "usage.promptTokens",
      "usage.completionTokens",
      "usage.totalTokens"
  ));

  @Valid
  private SessionProperties session = new SessionProperties();

  @Valid
  private Map<String, ProviderProperties> providers = new LinkedHashMap<>();

  public boolean isConfigured() {
    return hasUsableProvider(resolveDefaultProvider());
  }

  public String resolveDefaultProvider() {
    if (hasText(defaultProvider)) {
      return defaultProvider.trim();
    }
    return resolvedProviders().keySet().stream().findFirst().orElse("");
  }

  public String resolveDefaultModel() {
    return resolveModel(resolveDefaultProvider(), defaultModel);
  }

  public boolean hasUsableProvider(String providerName) {
    ProviderProperties providerProperties = getProvider(providerName);
    if (providerProperties == null || !providerProperties.isEnabled()) {
      return false;
    }
    return providerProperties.isConfigured(resolveModel(providerName, null));
  }

  public ProviderProperties getProvider(String providerName) {
    if (!hasText(providerName)) {
      return null;
    }
    return resolvedProviders().get(providerName.trim());
  }

  public String resolveModel(String providerName, String requestedModel) {
    if (hasText(requestedModel)) {
      return requestedModel.trim();
    }
    ProviderProperties providerProperties = getProvider(providerName);
    if (providerProperties != null && hasText(providerProperties.getDefaultModel())) {
      return providerProperties.getDefaultModel().trim();
    }
    if (hasText(defaultModel)) {
      return defaultModel.trim();
    }
    return "";
  }

  public List<String> availableModels(String providerName) {
    ProviderProperties providerProperties = getProvider(providerName);
    if (providerProperties == null || !providerProperties.isEnabled()) {
      return List.of();
    }
    LinkedHashSet<String> values = new LinkedHashSet<>();
    providerProperties.getModels().stream().filter(this::hasText).map(String::trim).forEach(values::add);
    String resolvedModel = resolveModel(providerName, null);
    if (hasText(resolvedModel)) {
      values.add(resolvedModel);
    }
    return List.copyOf(values);
  }

  public boolean isAllowedModel(String providerName, String requestedModel) {
    String resolvedModel = resolveModel(providerName, requestedModel);
    if (!hasText(resolvedModel)) {
      return false;
    }
    List<String> availableModels = availableModels(providerName);
    if (availableModels.isEmpty()) {
      return true;
    }
    return availableModels.stream().anyMatch(modelValue -> modelValue.equalsIgnoreCase(resolvedModel));
  }

  public Map<String, ProviderProperties> resolvedProviders() {
    LinkedHashMap<String, ProviderProperties> resolved = new LinkedHashMap<>();
    providers.forEach((name, properties) -> {
      if (hasText(name) && properties != null) {
        resolved.put(name.trim(), properties.copyWithDefaults(timeoutMillis));
      }
    });
    return resolved;
  }

  @AssertTrue(message = "当 llm.enabled=true 时，必须至少配置一个可用 provider 和 model")
  public boolean isProviderConfigValid() {
    return !enabled || resolvedProviders().entrySet().stream()
        .anyMatch(entry -> entry.getValue().isEnabled() && entry.getValue().isConfigured(resolveModel(entry.getKey(), null)));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @Getter
  @Setter
  public static class SessionProperties {

    private int maxSessionsPerDocument = 20;

    private int maxMessagesPerSession = 200;

    private int retentionDays = 30;
  }

  @Getter
  @Setter
  public static class ProviderProperties {

    private boolean enabled = true;

    private String label = "";

    private String springAiProvider = "openai-compatible";

    private String baseUrl = "";

    private String apiKey = "";

    private String defaultModel = "";

    private List<String> models = new ArrayList<>();

    private boolean streamingEnabled = true;

    private long timeoutMillis = 0L;

    private boolean supportsUpstreamCancel = false;

    public boolean isConfigured(String resolvedModel) {
      return hasText(apiKey) && hasText(resolvedModel);
    }

    private ProviderProperties copyWithDefaults(long inheritedTimeoutMillis) {
      ProviderProperties copy = new ProviderProperties();
      copy.enabled = enabled;
      copy.label = label;
      copy.springAiProvider = springAiProvider;
      copy.baseUrl = baseUrl;
      copy.apiKey = apiKey;
      copy.defaultModel = defaultModel;
      copy.models = new ArrayList<>(models);
      copy.streamingEnabled = streamingEnabled;
      copy.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : inheritedTimeoutMillis;
      copy.supportsUpstreamCancel = supportsUpstreamCancel;
      return copy;
    }

    private boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }
}
