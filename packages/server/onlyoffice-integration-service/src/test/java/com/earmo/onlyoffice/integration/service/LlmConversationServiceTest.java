package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.exception.LlmApiException;
import com.earmo.onlyoffice.integration.service.llm.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void shouldUseActiveVariantTextForAssistantHistory() {
        LlmProperties properties = new LlmProperties();
        properties.setHistoryBudgetTokens(100);
        properties.setDefaultSystemPrompt("sys");
        LlmPromptWindowBuilder builder = new LlmPromptWindowBuilder();

        DocumentLlmMessageEntity assistant = message("assistant", "非 active 版本");
        assistant.setMessageId("assistant-active");

        List<LlmProviderMessage> prompt = builder.buildMessages(
                properties,
                List.of(assistant),
                Map.of("assistant-active", "active 版本"),
                "后续问题",
                "",
                true,
                false,
                ""
        );

        assertThat(prompt).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("assistant");
            assertThat(message.content()).isEqualTo("active 版本");
        });
        assertThat(prompt.toString()).doesNotContain("非 active 版本");
    }

    @Test
    void shouldExposeProviderAndUsageMetaFromStreamChunk() {
        SpringAiProviderChunk chunk = new StubProvider(true).stream(new LlmRuntimeRequest(
                "stub-provider",
                "http://127.0.0.1:18089",
                "test-api-key",
                "fake-gpt",
                60000L,
                List.of(new LlmProviderMessage("user", "providerResponseMeta success"))
        )).blockLast();

        assertThat(chunk).isNotNull();
        assertThat(chunk.providerResponseMeta()).containsEntry("provider", "stub-provider");
        assertThat(chunk.providerResponseMeta()).containsEntry("model", "fake-gpt");
        assertThat(chunk.providerResponseMeta()).containsKey("usage");
        assertThat(chunk.providerResponseMeta().toString()).doesNotContain("system_fingerprint");
    }

    @Test
    void shouldMapBadRequestToMachineReadableErrorCode() {
        SpringAiLlmProvider provider = new StubProvider(false);
        assertThatThrownBy(() -> provider.stream(new LlmRuntimeRequest(
                "stub-provider",
                "http://127.0.0.1:18089",
                "test-api-key",
                "fake-gpt",
                60000L,
                List.of(new LlmProviderMessage("user", "BAD_REQUEST"))
        )).blockLast())
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("模型请求参数无效")
                .extracting(error -> ((LlmApiException) error).errorCode())
                .isEqualTo(LlmErrorCodes.LLM_PROVIDER_BAD_REQUEST);
    }

    @Test
    void shouldDiscardLateSuccessAfterRequestMarkedCancelled() {
        LlmRequestExecutionRegistry registry = new LlmRequestExecutionRegistry();
        registry.register("req-1", new StubProvider(true));
        registry.attachProviderRequestId("req-1", "upstream-1");
        registry.cancel("req-1");

        assertThat(registry.isCancelled("req-1")).isTrue();
    }

    @Test
    void shouldDisposeStreamSubscriptionWhenRequestCancelled() {
        LlmRequestExecutionRegistry registry = new LlmRequestExecutionRegistry();
        AtomicBoolean disposed = new AtomicBoolean(false);

        registry.register("req-2", new StubProvider(true));
        registry.attachStreamSubscription("req-2", new Disposable() {
            @Override
            public void dispose() {
                disposed.set(true);
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        });

        registry.cancel("req-2");

        assertThat(disposed.get()).isTrue();
        assertThat(registry.isCancelled("req-2")).isTrue();
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

    private static final class StubProvider implements SpringAiLlmProvider {

        private final boolean success;

        private StubProvider(boolean success) {
            this.success = success;
        }

        @Override
        public String providerName() {
            return "stub-provider";
        }

        @Override
        public Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request) {
            if (!success) {
                return Flux.error(new LlmApiException(
                        LlmErrorCodes.LLM_PROVIDER_BAD_REQUEST,
                        HttpStatus.BAD_REQUEST,
                        "模型请求参数无效。"
                ));
            }
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", 16);
            usage.put("completionTokens", 24);
            usage.put("totalTokens", 40);
            return Flux.just(new SpringAiProviderChunk(
                    "流式回复",
                    "provider-request-1",
                    new LlmProviderUsage(16, 24, 40),
                    "stop",
                    Map.of(
                            "provider", request.providerName(),
                            "model", request.model(),
                            "created", 1711000000,
                            "usage", usage
                    )
            ));
        }

        @Override
        public boolean supportsUpstreamCancel() {
            return true;
        }

        @Override
        public void cancelRequest(String providerRequestId) {
        }
    }
}
