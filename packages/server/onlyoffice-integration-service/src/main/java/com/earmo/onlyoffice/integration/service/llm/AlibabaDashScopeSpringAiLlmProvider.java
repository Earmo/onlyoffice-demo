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

@Component
public class AlibabaDashScopeSpringAiLlmProvider implements SpringAiLlmProvider {

  private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
  private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
  private final ObjectProvider<ObservationRegistry> observationRegistryProvider;
  private final ObjectProvider<ToolCallingManager> toolCallingManagerProvider;
  private final ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicateProvider;
  private final ObjectProvider<RetryTemplate> retryTemplateProvider;

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

  @Override
  public String providerName() {
    return "dashscope";
  }

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

  @Override
  public boolean supportsUpstreamCancel() {
    return false;
  }

  @Override
  public void cancelRequest(String providerRequestId) {
    // DashScope 这里先保留本地取消优先；上游取消未来按 provider 能力补上。
  }

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

  private LlmProviderUsage usage(Object usageObject) {
    return new LlmProviderUsage(
        valueAsInteger(invokeIfPresent(usageObject, "getPromptTokens")),
        valueAsInteger(invokeIfPresent(usageObject, "getCompletionTokens")),
        valueAsInteger(invokeIfPresent(usageObject, "getTotalTokens"))
    );
  }

  private Map<String, Object> usageMap(Object usageObject) {
    Map<String, Object> usageMap = new LinkedHashMap<>();
    usageMap.put("promptTokens", valueAsInteger(invokeIfPresent(usageObject, "getPromptTokens")));
    usageMap.put("completionTokens", valueAsInteger(invokeIfPresent(usageObject, "getCompletionTokens")));
    usageMap.put("totalTokens", valueAsInteger(invokeIfPresent(usageObject, "getTotalTokens")));
    return usageMap;
  }

  private String valueAsString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Integer valueAsInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }
}
