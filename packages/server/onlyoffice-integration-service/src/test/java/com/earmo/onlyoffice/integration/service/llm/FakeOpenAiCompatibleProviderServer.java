package com.earmo.onlyoffice.integration.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * fake provider：不走真实 socket，直接绑定 RestClient.Builder 做内存级拦截。
 */
public final class FakeOpenAiCompatibleProviderServer {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final MockRestServiceServer server;

  public FakeOpenAiCompatibleProviderServer(RestClient.Builder builder) {
    this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
  }

  public void enqueueSuccess(String assistantText) {
    enqueueResponse(withSuccess(successPayloadUnchecked(assistantText), MediaType.APPLICATION_JSON));
  }

  public void enqueueSlowSuccess(String assistantText, Duration delay) {
    server.expect(requestTo(containsString("/chat/completions")))
        .andRespond(request -> {
          sleep(delay);
          DefaultResponseCreator responseCreator = withSuccess(successPayloadUnchecked(assistantText), MediaType.APPLICATION_JSON);
          return responseCreator.createResponse(request);
        });
  }

  public void enqueueBadRequest() {
    enqueueResponse(withStatus(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":{\"message\":\"bad request\"}}"));
  }

  public void enqueueUpstreamError() {
    enqueueResponse(withStatus(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":{\"message\":\"upstream failed\"}}"));
  }

  public void verify() {
    server.verify();
  }

  private void enqueueResponse(DefaultResponseCreator responseCreator) {
    server.expect(requestTo(containsString("/chat/completions")))
        .andRespond(responseCreator);
  }

  private String successPayload(String assistantText) throws JsonProcessingException {
    Map<String, Object> usage = new LinkedHashMap<>();
    usage.put("prompt_tokens", 16);
    usage.put("completion_tokens", 24);
    usage.put("total_tokens", 40);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", "fake-request-1");
    payload.put("created", 1711000000);
    payload.put("model", "fake-gpt");
    payload.put("system_fingerprint", "sensitive-fingerprint");
    payload.put("usage", usage);
    payload.put("choices", List.of(Map.of(
        "index", 0,
        "finish_reason", "stop",
        "message", Map.of("role", "assistant", "content", assistantText)
    )));
    return OBJECT_MAPPER.writeValueAsString(payload);
  }

  private String successPayloadUnchecked(String assistantText) {
    try {
      return successPayload(assistantText);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("构造 fake provider 响应失败。", exception);
    }
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
