package com.earmo.onlyoffice.integration.service.llm;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * 阿里云 DashScope 的 Spring AI 适配器。
 *
 * <p>这里负责把领域层统一的 {@link LlmRuntimeRequest} 转成 DashScope Spring AI SDK 能理解的请求，
 * 并把流式响应重新折叠为 {@link SpringAiProviderChunk}。
 */
@Component
public class AlibabaDashScopeSpringAiLlmProvider implements SpringAiLlmProvider {

  private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
  private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
  private final ObjectProvider<ObservationRegistry> observationRegistryProvider;
  private final ObjectProvider<ToolCallingManager> toolCallingManagerProvider;
  private final ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicateProvider;
  private final ObjectProvider<RetryTemplate> retryTemplateProvider;

  /**
   * 注入构建 DashScope ChatModel 所需的依赖。
   */
  public AlibabaDashScopeSpringAiLlmProvider(
      ObjectProvider<RestClient.Builder> restClientBuilderProvider,
      ObjectProvider<WebClient.Builder> webClientBuilderProvider,
      ObjectProvider<ObservationRegistry> observationRegistryProvider,
      ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
      ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicateProvider,
      ObjectProvider<RetryTemplate> retryTemplateProvider
  ) {
    this.restClientBuilderProvider = restClientBuilderProvider;
    this.webClientBuilderProvider = webClientBuilderProvider;
    this.observationRegistryProvider = observationRegistryProvider;
    this.toolCallingManagerProvider = toolCallingManagerProvider;
    this.toolExecutionEligibilityPredicateProvider = toolExecutionEligibilityPredicateProvider;
    this.retryTemplateProvider = retryTemplateProvider;
  }

  /**
   * 返回当前 provider 在注册表中的实现名。
   */
  @Override
  public String providerName() {
    return "dashscope";
  }

  /**
   * 发起 DashScope 流式对话，并统一映射响应分片结构。
   *
   * <p>处理步骤：
   * 1. 构建 DashScopeChatModel；
   * 2. 组装 Spring AI Prompt；
   * 3. 逐帧读取文本、request id、usage 与 finish reason；
   * 4. 输出统一的 {@link SpringAiProviderChunk}。
   */
  @Override
  public Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request) {
    DashScopeChatModel chatModel = buildChatModel(request);
    Prompt prompt = new Prompt(
        toSpringAiMessages(request.messages()),
        DashScopeChatOptions.builder()
            .withModel(request.model())
            .withStream(true)
            .build()
    );
    return chatModel.stream(prompt)
        .map(response -> {
          Map<String, Object> meta = new LinkedHashMap<>();
          meta.put("model", valueAsString(invokeIfPresent(response.getMetadata(), "getModel")));
          meta.put("created", invokeIfPresent(response.getMetadata(), "getCreated"));
          meta.put("usage", usageMap(invokeIfPresent(response.getMetadata(), "getUsage")));
          return new SpringAiProviderChunk(
              readAssistantText(response),
              valueAsString(invokeIfPresent(response.getMetadata(), "getId")),
              usage(invokeIfPresent(response.getMetadata(), "getUsage")),
              valueAsString(invokeIfPresent(response.getResult() == null ? null : response.getResult().getMetadata(), "getFinishReason")),
              meta
          );
        });
  }

  /**
   * 按请求参数构造 DashScope ChatModel。
   */
  private DashScopeChatModel buildChatModel(LlmRuntimeRequest request) {
    ObservationRegistry observationRegistry = observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
    ToolCallingManager toolCallingManager = toolCallingManagerProvider.getIfAvailable(() ->
        new DefaultToolCallingManager(
            observationRegistry,
            new StaticToolCallbackResolver(List.of()),
            new DefaultToolExecutionExceptionProcessor(false)
        )
    );
    ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate =
        toolExecutionEligibilityPredicateProvider.getIfAvailable(DefaultToolExecutionEligibilityPredicate::new);
    RetryTemplate retryTemplate = retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE);

    DashScopeApi dashScopeApi = DashScopeApi.builder()
        .baseUrl(request.baseUrl())
        .apiKey(request.apiKey())
        .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
        .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
        .build();

    return DashScopeChatModel.builder()
        .dashScopeApi(dashScopeApi)
        .defaultOptions(DashScopeChatOptions.builder()
            .withModel(request.model())
            .withStream(true)
            .build())
        .observationRegistry(observationRegistry)
        .toolCallingManager(toolCallingManager)
        .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
        .retryTemplate(retryTemplate)
        .build();
  }

  /**
   * 反射调用目标对象上可能存在的方法。
   *
   * <p>某些 DashScope 元数据结构在不同 SDK 版本中不完全稳定，
   * 这里采用“有则读取、无则忽略”的兼容策略。
   */
  private Object invokeIfPresent(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    try {
      java.lang.reflect.Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 当前实现暂不支持上游取消。
   */
  @Override
  public boolean supportsUpstreamCancel() {
    return false;
  }

  /**
   * DashScope 当前先仅支持本地取消，不向上游转发取消。
   */
  @Override
  public void cancelRequest(String providerRequestId) {
    // DashScope 这里先保留本地取消优先；上游取消未来按 provider 能力补上。
  }

  /**
   * 把领域层消息映射成 Spring AI 消息类型。
   */
  private List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(List<LlmProviderMessage> messages) {
    return messages.stream()
        .map(message -> switch (message.role()) {
          case "system" -> new SystemMessage(message.content());
          case "assistant" -> new AssistantMessage(message.content());
          default -> new UserMessage(message.content());
        })
        .map(message -> (org.springframework.ai.chat.messages.Message) message)
        .toList();
  }

  /**
   * 从 DashScope 响应中提取 assistant 增量文本。
   */
  private String readAssistantText(Object response) {
    if (response == null) {
      return "";
    }
    Object result = invokeIfPresent(response, "getResult");
    Object output = invokeIfPresent(result, "getOutput");
    Object text = invokeIfPresent(output, "getText");
    if (text == null) {
      text = invokeIfPresent(output, "getContent");
    }
    return text == null ? "" : String.valueOf(text);
  }

  /**
   * 读取 usage 并转换为统一结构。
   */
  private LlmProviderUsage usage(Object usageObject) {
    return new LlmProviderUsage(
        valueAsInteger(invokeIfPresent(usageObject, "getPromptTokens")),
        valueAsInteger(invokeIfPresent(usageObject, "getCompletionTokens")),
        valueAsInteger(invokeIfPresent(usageObject, "getTotalTokens"))
    );
  }

  /**
   * 把 usage 信息转换为可序列化元数据。
   */
  private Map<String, Object> usageMap(Object usageObject) {
    Map<String, Object> usageMap = new LinkedHashMap<>();
    usageMap.put("promptTokens", valueAsInteger(invokeIfPresent(usageObject, "getPromptTokens")));
    usageMap.put("completionTokens", valueAsInteger(invokeIfPresent(usageObject, "getCompletionTokens")));
    usageMap.put("totalTokens", valueAsInteger(invokeIfPresent(usageObject, "getTotalTokens")));
    return usageMap;
  }

  /**
   * 安全地把任意值转成字符串。
   */
  private String valueAsString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 安全地把任意数字值转成整数。
   */
  private Integer valueAsInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }
}
