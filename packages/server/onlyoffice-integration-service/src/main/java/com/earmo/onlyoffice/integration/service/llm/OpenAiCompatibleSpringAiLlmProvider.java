package com.earmo.onlyoffice.integration.service.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

/**
 * 适配标准 OpenAI-compatible chat completions 的 provider。
 *
 * <p>Phase 14.2 中，SiliconFlow 这类兼容 OpenAI 协议的服务统一走这里，
 * 固定使用 /v1/chat/completions + stream=true，而不是任何厂商私有路径。
 */
@Component
public class OpenAiCompatibleSpringAiLlmProvider implements SpringAiLlmProvider {

  private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
      new ParameterizedTypeReference<>() {
      };

  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;

  /**
   * 注入 WebClient 构建器与 JSON 解析器。
   */
  public OpenAiCompatibleSpringAiLlmProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
    this.webClientBuilder = webClientBuilder;
    this.objectMapper = objectMapper;
  }

  /**
   * 返回当前 provider 在注册表中的实现名。
   */
  @Override
  public String providerName() {
    return "openai-compatible";
  }

  /**
   * 通过 OpenAI-compatible SSE 协议发起流式对话。
   *
   * <p>处理步骤：
   * 1. 仅构造最小必要 payload；
   * 2. 调用 `/chat/completions` 并声明 `text/event-stream`；
   * 3. 逐帧过滤空数据与 `[DONE]`；
   * 4. 把每帧响应解析成统一 chunk；
   * 5. 把底层客户端异常映射成稳定业务错误码。
   */
  @Override
  public Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request) {
    // 这里只构造 OpenAI-compatible 最小必要 payload：
    // model + stream + messages。其余会话状态在领域层维护，不向上游透传内部字段。
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", request.model());
    payload.put("stream", true);
    payload.put("messages", request.messages().stream().map(message -> Map.of(
        "role", message.role(),
        "content", message.content()
    )).toList());

    return webClient(request)
        .post()
        .uri("/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(payload)
        .retrieve()
        .bodyToFlux(SSE_STRING_TYPE)
        .mapNotNull(ServerSentEvent::data)
        .filter(data -> !data.isBlank())
        // OpenAI-compatible SSE 以 [DONE] 作为终止帧；真正的终态落库由上层服务统一负责。
        .takeUntil("[DONE]"::equals)
        .filter(data -> !"[DONE]".equals(data))
        .map(this::parseChunk)
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
   * 构建当前请求专属的 WebClient。
   */
  private WebClient webClient(LlmRuntimeRequest request) {
    HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMillis(request.timeoutMillis()));
    return webClientBuilder.clone()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .exchangeStrategies(ExchangeStrategies.withDefaults())
        // 配置允许只写域名或根路径，这里统一补到 /v1，避免上层把实现细节散落到各处。
        .baseUrl(normalizeBaseUrl(request.baseUrl()))
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + request.apiKey())
        .build();
  }

  /**
   * 解析单个 SSE 数据帧。
   */
  private SpringAiProviderChunk parseChunk(String data) {
    try {
      Map<String, Object> response = objectMapper.readValue(data, new TypeReference<>() {
      });
      List<?> choices = response.get("choices") instanceof List<?> list ? list : List.of();
      Map<?, ?> firstChoice = !choices.isEmpty() && choices.getFirst() instanceof Map<?, ?> choiceMap ? choiceMap : Map.of();
      Map<?, ?> delta = firstChoice.get("delta") instanceof Map<?, ?> deltaMap ? deltaMap : Map.of();

      Map<String, Object> providerMeta = new LinkedHashMap<>();
      providerMeta.put("model", response.get("model"));
      providerMeta.put("created", response.get("created"));
      Map<String, Object> normalizedUsage = normalizeUsageMap(response.get("usage"));
      if (!normalizedUsage.isEmpty()) {
        providerMeta.put("usage", normalizedUsage);
      }

      // 统一映射成领域层约定的 chunk 结构：
      // delta 负责增量展示，usage / finish_reason / providerMeta 留给 terminal path 汇总。
      return new SpringAiProviderChunk(
          stringValue(delta.get("content")),
          stringValue(response.get("id")),
          normalizeUsage(response.get("usage")),
          stringValue(firstChoice.get("finish_reason")),
          providerMeta
      );
    } catch (Exception exception) {
      throw new LlmApiException(
          LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR,
          HttpStatus.BAD_GATEWAY,
          "模型上游服务返回无法解析的响应。"
      );
    }
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
    return new LlmApiException(
        LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR,
        HttpStatus.BAD_GATEWAY,
        "模型上游服务返回无法解析的响应。"
    );
  }

  /**
   * 把 OpenAI-compatible usage 结构规范化为内部对象。
   */
  private LlmProviderUsage normalizeUsage(Object usageObject) {
    if (!(usageObject instanceof Map<?, ?> usageMap)) {
      return new LlmProviderUsage(null, null, null);
    }
    return new LlmProviderUsage(
        intValue(usageMap.get("prompt_tokens")),
        intValue(usageMap.get("completion_tokens")),
        intValue(usageMap.get("total_tokens"))
    );
  }

  /**
   * 把 usage map 规范化成前端/数据库使用的 camelCase 键名。
   */
  private Map<String, Object> normalizeUsageMap(Object usageObject) {
    if (!(usageObject instanceof Map<?, ?> usageMap)) {
      return Map.of();
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("promptTokens", intValue(usageMap.get("prompt_tokens")));
    normalized.put("completionTokens", intValue(usageMap.get("completion_tokens")));
    normalized.put("totalTokens", intValue(usageMap.get("total_tokens")));
    return normalized;
  }

  /**
   * 规范化 baseUrl，统一补到 `/v1` 根路径。
   */
  private String normalizeBaseUrl(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String trimmed = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    if (trimmed.endsWith("/v1")) {
      return trimmed;
    }
    return trimmed + "/v1";
  }

  /**
   * 安全地把任意值转成字符串。
   */
  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * 安全地把任意数字值转成整数。
   */
  private Integer intValue(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }
}
