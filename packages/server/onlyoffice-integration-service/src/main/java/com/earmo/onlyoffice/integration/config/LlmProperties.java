package com.earmo.onlyoffice.integration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
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

  @NotBlank
  private String provider = "openai-compatible";

  private String baseUrl = "";

  private String apiKey = "";

  private String model = "";

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
      "model",
      "created",
      "usage.promptTokens",
      "usage.completionTokens",
      "usage.totalTokens"
  ));

  @Valid
  private SessionProperties session = new SessionProperties();

  public boolean isConfigured() {
    return hasText(baseUrl) && hasText(apiKey) && hasText(model);
  }

  @AssertTrue(message = "当 llm.enabled=true 时，必须提供 llm.base-url、llm.api-key 和 llm.model")
  public boolean isProviderConfigValid() {
    return !enabled || isConfigured();
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
}
