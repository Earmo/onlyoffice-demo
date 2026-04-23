package com.earmo.onlyoffice.integration.service.llm;

import io.micrometer.observation.ObservationRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

/**
 * 基于 Spring AI OpenAI 模型适配标准 OpenAI-compatible chat completions 的 provider。
 *
 * <p>DashScope 兼容模式、SiliconFlow 这类兼容 OpenAI 协议的服务统一走这里，
 * 由 Spring AI OpenAI 实现负责请求构造和流式响应解析。
 */
@Component
@Slf4j
public class OpenAiCompatibleSpringAiLlmProvider implements SpringAiLlmProvider {

  private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
  private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
  private final ObjectProvider<ObservationRegistry> observationRegistryProvider;
  private final ObjectProvider<ToolCallingManager> toolCallingManagerProvider;
  private final ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicateProvider;
  private final ObjectProvider<RetryTemplate> retryTemplateProvider;

  /**
   * 注入构建 Spring AI OpenAI ChatModel 所需的依赖。
   */
  public OpenAiCompatibleSpringAiLlmProvider(
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
    return "openai-compatible";
  }

  /**
   * 通过 Spring AI OpenAI ChatModel 发起流式对话。
   *
   * <p>处理步骤：
   * 1. 为当前请求构造 OpenAiApi 与 OpenAiChatModel；
   * 2. 组装 Spring AI Prompt；
   * 3. 逐帧读取文本、request id、usage 与 finish reason；
   * 4. 把底层客户端异常映射成稳定业务错误码。
   */
  @Override
  public Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request) {
    OpenAiChatModel chatModel = buildChatModel(request);
    AtomicBoolean firstChunkLogged = new AtomicBoolean(false);
    Prompt prompt = new Prompt(
        toSpringAiMessages(request.messages()),
        OpenAiChatOptions.builder()
            .model(request.model())
            .streamUsage(true)
            .build()
    );
    log.info(
        "开始调用上游 LLM provider，provider={}, model={}, baseUrl={}, timeoutMs={}, messageCount={}",
        request.providerName(),
        request.model(),
        request.baseUrl(),
        request.timeoutMillis(),
        request.messages().size()
    );
    return chatModel.stream(prompt)
        .map(response -> {
          Object metadata = response.getMetadata();
          Object output = response.getResult() == null ? null : response.getResult().getOutput();
          Map<String, Object> providerMeta = new LinkedHashMap<>();
          providerMeta.put("model", valueAsString(invokeIfPresent(metadata, "getModel")));
          Object created = readMetadataValue(metadata, "created");
          if (created != null) {
            providerMeta.put("created", created);
          }
          String reasoningContent = readReasoningContent(output);
          if (reasoningContent != null && !reasoningContent.isBlank()) {
            providerMeta.put("reasoningContent", reasoningContent);
          }
          Map<String, Object> usageMap = usageMap(invokeIfPresent(metadata, "getUsage"));
          if (!usageMap.isEmpty()) {
            providerMeta.put("usage", usageMap);
          }
          return new SpringAiProviderChunk(
              readAssistantText(response),
              valueAsString(invokeIfPresent(metadata, "getId")),
              usage(invokeIfPresent(metadata, "getUsage")),
              valueAsString(invokeIfPresent(response.getResult() == null ? null : response.getResult().getMetadata(), "getFinishReason")),
              providerMeta
          );
        })
        .doOnNext(chunk -> {
          if (firstChunkLogged.compareAndSet(false, true)) {
            log.info(
                "收到首个上游 LLM 响应片段，provider={}, model={}, upstreamRequestId={}, hasDelta={}, finishReason={}",
                request.providerName(),
                request.model(),
                chunk.providerRequestId(),
                chunk.delta() != null && !chunk.delta().isEmpty(),
                chunk.finishReason()
            );
          }
        })
        .doOnComplete(() -> log.info(
            "上游 LLM 流已完成，provider={}, model={}",
            request.providerName(),
            request.model()
        ))
        .doOnError(exception -> log.info(
            "上游 LLM 流失败，provider={}, model={}, exceptionType={}, message={}",
            request.providerName(),
            request.model(),
            exception.getClass().getSimpleName(),
            exception.getMessage()
        ))
        .onErrorMap(this::mapException);
  }

  /**
   * 当前实现暂不支持上游取消。
   */
  @Override
  public boolean supportsUpstreamCancel() {
    return false;
  }

  /**
   * 当前 provider 不向上游发送取消请求。
   */
  @Override
  public void cancelRequest(String providerRequestId) {
  }

  /**
   * 为当前请求构造 OpenAI ChatModel。
   */
  private OpenAiChatModel buildChatModel(LlmRuntimeRequest request) {
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

    OpenAiApi openAiApi = OpenAiApi.builder()
        .baseUrl(normalizeBaseUrl(request.baseUrl()))
        .apiKey(request.apiKey())
        .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
        .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
        .build();

    return OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(OpenAiChatOptions.builder()
            .model(request.model())
            .streamUsage(true)
            .build())
        .observationRegistry(observationRegistry)
        .toolCallingManager(toolCallingManager)
        .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
        .retryTemplate(retryTemplate)
        .build();
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
   * 把 WebClient / 解析异常映射成统一业务异常。
   */
  private RuntimeException mapException(Throwable throwable) {
    // 对外只暴露稳定的业务错误码，不把 WebClient 的细节直接抛到 controller / 前端。
    if (throwable instanceof LlmApiException exception) {
      return exception;
    }
    if (throwable instanceof WebClientResponseException exception) {
      if (exception.getStatusCode().is4xxClientError()) {
        return new LlmApiException(
            LlmErrorCodes.LLM_PROVIDER_BAD_REQUEST,
            HttpStatus.valueOf(exception.getStatusCode().value()),
            "模型请求参数无效。"
        );
      }
      return new LlmApiException(
          LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR,
          HttpStatus.BAD_GATEWAY,
          "模型上游服务返回异常。"
      );
    }
    if (throwable instanceof WebClientRequestException) {
      return new LlmApiException(
          LlmErrorCodes.LLM_PROVIDER_TIMEOUT,
          HttpStatus.GATEWAY_TIMEOUT,
          "模型上游服务响应超时。"
      );
    }
    if (throwable instanceof TimeoutException) {
      return new LlmApiException(
          LlmErrorCodes.LLM_PROVIDER_TIMEOUT,
          HttpStatus.GATEWAY_TIMEOUT,
          "模型上游服务响应超时。"
      );
    }
    return new LlmApiException(
        LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR,
        HttpStatus.BAD_GATEWAY,
        "模型上游服务返回无法解析的响应。"
    );
  }

  /**
   * 反射调用目标对象上可能存在的方法。
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
   * 从 ChatResponseMetadata 的 key-value 中读取指定值。
   */
  private Object readMetadataValue(Object metadata, String key) {
    if (metadata == null || key == null || key.isBlank()) {
      return null;
    }
    try {
      java.lang.reflect.Method method = metadata.getClass().getMethod("get", String.class);
      return method.invoke(metadata, key);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 从响应中提取 assistant 增量文本。
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
   * 从 AssistantMessage 元数据中提取推理内容。
   *
   * <p>Spring AI 会把 OpenAI-compatible 的 `reasoning_content` 映射成
   * AssistantMessage metadata 上的 `reasoningContent` 字段，这里统一做兼容读取。
   */
  private String readReasoningContent(Object output) {
    if (output == null) {
      return null;
    }
    Object metadata = invokeIfPresent(output, "getMetadata");
    Object reasoningContent = metadata instanceof Map<?, ?> metadataMap
        ? metadataMap.get("reasoningContent")
        : invokeIfPresent(metadata, "getReasoningContent");
    if (reasoningContent == null) {
      return null;
    }
    String value = String.valueOf(reasoningContent).trim();
    return value.isEmpty() ? null : value;
  }

  /**
   * 把 usage 信息转换为内部统一结构。
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
    Integer promptTokens = valueAsInteger(invokeIfPresent(usageObject, "getPromptTokens"));
    Integer completionTokens = valueAsInteger(invokeIfPresent(usageObject, "getCompletionTokens"));
    Integer totalTokens = valueAsInteger(invokeIfPresent(usageObject, "getTotalTokens"));
    if (promptTokens != null) {
      usageMap.put("promptTokens", promptTokens);
    }
    if (completionTokens != null) {
      usageMap.put("completionTokens", completionTokens);
    }
    if (totalTokens != null) {
      usageMap.put("totalTokens", totalTokens);
    }
    return usageMap;
  }

  /**
   * 规范化 baseUrl，转换到 OpenAiApi 期望的根路径。
   */
  private String normalizeBaseUrl(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String trimmed = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    if (trimmed.endsWith("/chat/completions")) {
      trimmed = trimmed.substring(0, trimmed.length() - "/chat/completions".length());
    }
    if (trimmed.endsWith("/v1")) {
      trimmed = trimmed.substring(0, trimmed.length() - 3);
    }
    return trimmed;
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
