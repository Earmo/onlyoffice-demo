package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleLlmProviderStrategy implements LlmProviderStrategy {

  private final RestClient.Builder restClientBuilder;
  private final LlmProperties llmProperties;

  @Override
  public String providerName() {
    return "openai-compatible";
  }

  @Override
  public LlmProviderResponse sendChat(LlmProviderRequest request) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("model", request.model());
      payload.put("messages", request.messages().stream().map(message -> Map.of(
          "role", message.role(),
          "content", message.content()
      )).toList());

      Map<?, ?> response = buildClient()
          .post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(Map.class);

      String assistantText = "";
      String finishReason = null;
      if (response != null && response.get("choices") instanceof List<?> choices && !choices.isEmpty()) {
        Object firstChoice = choices.getFirst();
        if (firstChoice instanceof Map<?, ?> choiceMap) {
          if (choiceMap.get("message") instanceof Map<?, ?> messageMap) {
            Object content = messageMap.containsKey("content") ? messageMap.get("content") : "";
            assistantText = content == null ? "" : String.valueOf(content);
          }
          finishReason = valueAsString(choiceMap.get("finish_reason"));
        }
      }

      Map<String, Object> providerResponseMeta = new LinkedHashMap<>();
      providerResponseMeta.put("model", response == null ? null : response.get("model"));
      providerResponseMeta.put("created", response == null ? null : response.get("created"));
      providerResponseMeta.put("usage", normalizeUsageMap(response == null ? null : response.get("usage")));

      return new LlmProviderResponse(
          valueAsString(response == null ? null : response.get("id")),
          assistantText == null ? "" : assistantText,
          normalizeUsage(response == null ? null : response.get("usage")),
          finishReason,
          providerResponseMeta
      );
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().is4xxClientError()) {
        throw new LlmApiException(
            LlmErrorCodes.LLM_PROVIDER_BAD_REQUEST,
            org.springframework.http.HttpStatus.valueOf(exception.getStatusCode().value()),
            "模型请求参数无效。"
        );
      }
      throw new LlmApiException(
          LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR,
          org.springframework.http.HttpStatus.BAD_GATEWAY,
          "模型上游服务返回异常。"
      );
    } catch (ResourceAccessException exception) {
      throw new LlmApiException(
          LlmErrorCodes.LLM_PROVIDER_TIMEOUT,
          org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
          "模型上游服务响应超时。"
      );
    }
  }

  @Override
  public boolean supportsUpstreamCancel() {
    return false;
  }

  @Override
  public void cancelRequest(String providerRequestId) {
    // openai-compatible 默认没有稳定取消端点；这里只保留 best effort 扩展位。
  }

  private RestClient buildClient() {
    return restClientBuilder
        .baseUrl(trimTrailingSlash(llmProperties.getBaseUrl()))
        .defaultHeader("Authorization", "Bearer " + llmProperties.getApiKey())
        .build();
  }

  private String trimTrailingSlash(String value) {
    if (value == null) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private LlmProviderUsage normalizeUsage(Object usageObject) {
    if (!(usageObject instanceof Map<?, ?> usageMap)) {
      return new LlmProviderUsage(null, null, null);
    }
    return new LlmProviderUsage(
        valueAsInteger(usageMap.get("prompt_tokens")),
        valueAsInteger(usageMap.get("completion_tokens")),
        valueAsInteger(usageMap.get("total_tokens"))
    );
  }

  private Map<String, Object> normalizeUsageMap(Object usageObject) {
    if (!(usageObject instanceof Map<?, ?> usageMap)) {
      return Map.of();
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("promptTokens", valueAsInteger(usageMap.get("prompt_tokens")));
    normalized.put("completionTokens", valueAsInteger(usageMap.get("completion_tokens")));
    normalized.put("totalTokens", valueAsInteger(usageMap.get("total_tokens")));
    return normalized;
  }

  private String valueAsString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Integer valueAsInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }
}
