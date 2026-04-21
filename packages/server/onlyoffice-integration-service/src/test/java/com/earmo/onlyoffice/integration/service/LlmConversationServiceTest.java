package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.service.llm.FakeOpenAiCompatibleProviderServer;
import com.earmo.onlyoffice.integration.service.llm.LlmApiException;
import com.earmo.onlyoffice.integration.service.llm.LlmPromptWindowBuilder;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderRequest;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderResponse;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderStrategy;
import com.earmo.onlyoffice.integration.service.llm.LlmRequestExecutionRegistry;
import com.earmo.onlyoffice.integration.service.llm.OpenAiCompatibleLlmProviderStrategy;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConversationServiceTest {

  @Test
  void shouldTrimHistoryByCharsDiv4Budget() {
    LlmProperties properties = new LlmProperties();
    properties.setHistoryBudgetTokens(9);
    properties.setDefaultSystemPrompt("sys");
    properties.setHistoryTokenEstimator("chars_div_4");
    LlmPromptWindowBuilder builder = new LlmPromptWindowBuilder();

    DocumentLlmMessageEntity oldUser = message("user", "这是一个很长很长的旧问题，应该被预算裁掉。");
    DocumentLlmMessageEntity latestAssistant = message("assistant", "最近一轮回复");

    List<?> prompt = builder.buildMessages(
        properties,
        List.of(oldUser, latestAssistant),
        "Q",
        "S",
        false,
        true,
        "H"
    );

    String promptText = prompt.toString();
    assertThat(properties.getHistoryTokenEstimator()).isEqualTo("chars_div_4");
    assertThat(promptText).contains("问题：");
    assertThat(promptText).doesNotContain("很长很长的旧问题");
  }

  @Test
  void shouldFilterProviderResponseMetaAndMapBadRequestErrorCode() throws Exception {
    LlmProviderResponse response = buildStrategyWithSuccess().sendChat(new LlmProviderRequest(
        "fake-gpt",
        List.of(new com.earmo.onlyoffice.integration.service.llm.LlmProviderMessage("user", "providerResponseMeta success"))
    ));

    assertThat(response.providerResponseMeta()).containsKeys("model", "created", "usage");
    assertThat(response.providerResponseMeta().toString()).doesNotContain("system_fingerprint");

    OpenAiCompatibleLlmProviderStrategy badRequestStrategy = buildStrategyWithBadRequest();
    assertThatThrownBy(() -> badRequestStrategy.sendChat(new LlmProviderRequest(
        "fake-gpt",
        List.of(new com.earmo.onlyoffice.integration.service.llm.LlmProviderMessage("user", "BAD_REQUEST"))
    )))
        .isInstanceOf(LlmApiException.class)
        .hasMessageContaining("模型请求参数无效");
  }

  @Test
  void shouldDiscardLateSuccessAfterRequestMarkedCancelled() {
    LlmRequestExecutionRegistry registry = new LlmRequestExecutionRegistry();
    registry.register("req-1", new NoopProviderStrategy());
    registry.attachProviderRequestId("req-1", "upstream-1");
    registry.cancel("req-1");

    assertThat(registry.isCancelled("req-1")).isTrue();
  }

  private static DocumentLlmMessageEntity message(String role, String content) {
    DocumentLlmMessageEntity entity = new DocumentLlmMessageEntity();
    entity.setMessageId(role + "-1");
    entity.setRole(role);
    entity.setMessageText("user".equals(role) ? content : null);
    entity.setAssistantText("assistant".equals(role) ? content : null);
    entity.setCreatedTime(Instant.now());
    entity.setStatus("completed");
    return entity;
  }

  private static final class NoopProviderStrategy implements LlmProviderStrategy {

    @Override
    public String providerName() {
      return "noop";
    }

    @Override
    public com.earmo.onlyoffice.integration.service.llm.LlmProviderResponse sendChat(
        com.earmo.onlyoffice.integration.service.llm.LlmProviderRequest request
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsUpstreamCancel() {
      return true;
    }

    @Override
    public void cancelRequest(String providerRequestId) {
    }
  }

  private OpenAiCompatibleLlmProviderStrategy buildStrategyWithSuccess() {
    RestClient.Builder builder = RestClient.builder();
    FakeOpenAiCompatibleProviderServer fakeProvider = new FakeOpenAiCompatibleProviderServer(builder);
    fakeProvider.enqueueSuccess("providerResponseMeta success");
    return new OpenAiCompatibleLlmProviderStrategy(builder, baseProperties());
  }

  private OpenAiCompatibleLlmProviderStrategy buildStrategyWithBadRequest() {
    RestClient.Builder builder = RestClient.builder();
    FakeOpenAiCompatibleProviderServer fakeProvider = new FakeOpenAiCompatibleProviderServer(builder);
    fakeProvider.enqueueBadRequest();
    return new OpenAiCompatibleLlmProviderStrategy(builder, baseProperties());
  }

  private LlmProperties baseProperties() {
    LlmProperties properties = new LlmProperties();
    properties.setBaseUrl("http://fake-provider.test");
    properties.setApiKey("test-key");
    properties.setModel("fake-gpt");
    return properties;
  }
}
