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

  /** 是否启用 LLM 后端链路；关闭时接口会返回不可用能力。 */
  private boolean enabled = false;

  /** 是否向前端开放 AI 功能入口；用于灰度关闭 UI 能力。 */
  private boolean featureEnabled = true;

  /** 默认逻辑 provider 名称，对应 providers map 的 key。 */
  private String defaultProvider = "dashscope";

  /** 全局默认模型；provider 未指定 defaultModel 时会回退到这里。 */
  private String defaultModel = "";

  /** 单次 provider 调用默认超时时间。 */
  private long timeoutMillis = 60000L;

  /** 非流式同步等待请求完成的最长时间。 */
  private long requestSyncWaitMillis = 1500L;

  /** 构建 prompt 历史窗口时默认保留的可见对话轮数。 */
  private int defaultVisibleTurns = 6;

  /** prompt 历史窗口的 token 预算上限。 */
  private int historyBudgetTokens = 12000;

  /** 历史 token 估算策略名称。 */
  @NotBlank
  private String historyTokenEstimator = "chars_div_4";

  /** 是否允许把当前章节标题作为上下文放入 prompt。 */
  private boolean allowHeadingContext = true;

  /** 默认 system prompt。 */
  @NotBlank
  private String defaultSystemPrompt = "你是一个文档协作助手。请基于用户给出的选区和标题上下文，输出可直接写回文档的专业中文建议。";

  /** 允许透传到前端的 provider 元数据字段路径白名单。 */
  private List<String> providerResponseMetaAllowlist = new ArrayList<>(List.of(
      "provider",
      "model",
      "created",
      "reasoningContent",
      "usage.promptTokens",
      "usage.completionTokens",
      "usage.totalTokens"
  ));

  @Valid
  private SessionProperties session = new SessionProperties();

  /** 已配置的逻辑 provider 集合，key 是前端和接口使用的 provider 名称。 */
  @Valid
  private Map<String, ProviderProperties> providers = new LinkedHashMap<>();

  /**
   * 判断默认 provider 是否满足最低可用配置。
   */
  public boolean isConfigured() {
    return hasUsableProvider(resolveDefaultProvider());
  }

  /**
   * 解析默认 provider。
   *
   * <p>优先使用 defaultProvider；为空时回退到第一个已声明 provider。
   */
  public String resolveDefaultProvider() {
    if (hasText(defaultProvider)) {
      return defaultProvider.trim();
    }
    return resolvedProviders().keySet().stream().findFirst().orElse("");
  }

  /**
   * 解析默认模型。
   *
   * <p>优先使用默认 provider 的 defaultModel，其次使用全局 defaultModel。
   */
  public String resolveDefaultModel() {
    return resolveModel(resolveDefaultProvider(), defaultModel);
  }

  /**
   * 判断指定 provider 是否已启用且具备可用模型。
   */
  public boolean hasUsableProvider(String providerName) {
    ProviderProperties providerProperties = getProvider(providerName);
    if (providerProperties == null || !providerProperties.isEnabled()) {
      return false;
    }
    return providerProperties.isConfigured(resolveModel(providerName, null));
  }

  /**
   * 按 provider 名称获取已合并默认值的配置。
   */
  public ProviderProperties getProvider(String providerName) {
    if (!hasText(providerName)) {
      return null;
    }
    return resolvedProviders().get(providerName.trim());
  }

  /**
   * 解析一次请求最终应使用的模型。
   *
   * <p>优先级：请求模型 > provider 默认模型 > 全局默认模型。
   */
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

  /**
   * 返回 provider 对外可选的模型列表。
   *
   * <p>显式配置 models 时使用配置列表，同时确保解析后的默认模型也包含在结果中。
   */
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

  /**
   * 判断请求模型是否落在 provider 允许范围内。
   */
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

  /**
   * 返回已合并继承默认值的 provider 配置集合。
   */
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

    /** 单文档单用户最多保留的 AI 会话数。 */
    private int maxSessionsPerDocument = 20;

    /** 单会话最多保留的消息数。 */
    private int maxMessagesPerSession = 200;

    /** 会话保留天数，供后续归档清理策略使用。 */
    private int retentionDays = 30;
  }

  @Getter
  @Setter
  public static class ProviderProperties {

    /** 是否启用该逻辑 provider。 */
    private boolean enabled = true;

    /** 前端展示名；为空时使用 provider key。 */
    private String label = "";

    /** 底层适配器类型，例如 openai-compatible。 */
    private String springAiProvider = "openai-compatible";

    /** OpenAI-compatible base URL。 */
    private String baseUrl = "";

    /** provider API key，禁止在日志中输出。 */
    private String apiKey = "";

    /** provider 默认模型。 */
    private String defaultModel = "";

    /** 前端允许选择的模型列表。 */
    private List<String> models = new ArrayList<>();

    /** 是否走流式输出。 */
    private boolean streamingEnabled = true;

    /** provider 专属超时时间；小于等于 0 时继承全局 timeoutMillis。 */
    private long timeoutMillis = 0L;

    /** 是否支持向上游 provider 传播取消请求。 */
    private boolean supportsUpstreamCancel = false;

    /**
     * 判断 provider 是否具备最小可用配置。
     */
    public boolean isConfigured(String resolvedModel) {
      return hasText(apiKey) && hasText(resolvedModel);
    }

    /**
     * 复制 provider 配置并填充继承默认值。
     */
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
