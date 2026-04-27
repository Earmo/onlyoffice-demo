package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.service.llm.LlmApiException;
import com.earmo.onlyoffice.integration.service.llm.LlmErrorCodes;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderMessage;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderUsage;
import com.earmo.onlyoffice.integration.service.llm.LlmRuntimeRequest;
import com.earmo.onlyoffice.integration.service.llm.SpringAiLlmProvider;
import com.earmo.onlyoffice.integration.service.llm.SpringAiProviderChunk;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LlmConversationFlowTest {

  @TestConfiguration
  static class TestProviderConfig {

    @Bean
    StubStreamingProvider stubStreamingProvider() {
      return new StubStreamingProvider();
    }

    @Bean
    @Primary
    SpringAiLlmProvider springAiLlmProvider(StubStreamingProvider stubStreamingProvider) {
      return stubStreamingProvider;
    }
  }

  @org.springframework.beans.factory.annotation.Autowired
  private MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  @Qualifier("stubStreamingProvider")
  private StubStreamingProvider stubStreamingProvider;

  @BeforeEach
  void setUp() {
    stubStreamingProvider.reset();
  }

  @Test
  void shouldRunCapabilitySessionMessageStreamFlow() throws Exception {
    stubStreamingProvider.enqueueSuccess("这是模型返回的建议。", Duration.ofMillis(10));
    String documentId = createDocument("llm-user", "LLM User");

    mockMvc.perform(get("/api/llm/capability").param("documentId", documentId).headers(TestAccessHeaders.headers("llm-user", "LLM User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.llmAvailable").value(true))
        .andExpect(jsonPath("$.defaultProvider").value("stub-provider"))
        .andExpect(jsonPath("$.availableProviders[0].provider").value("stub-provider"));

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult streamResult = mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"STREAM 首轮问题",
                      "selectionSnapshot":{"text":"选区内容","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    Thread.sleep(120L);
    String streamBody = streamResult.getResponse().getContentAsString();
    assertThat(streamBody).contains("event:request-started");
    assertThat(streamBody).contains("event:reasoning-delta");
    assertThat(streamBody).contains("event:assistant-delta");
    assertThat(streamBody).contains("event:assistant-completed");
    assertThat(streamBody.indexOf("event:reasoning-delta")).isLessThan(streamBody.indexOf("event:assistant-delta"));
    assertThat(streamBody).contains("\"reasoningText\":");

    String requestId = jsonFieldFromSse(streamBody, "requestId");
    assertThat(requestId).isNotBlank();

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completed"))
        .andExpect(jsonPath("$.assistantText").value("这是模型返回的建议。"))
        .andExpect(jsonPath("$.assistantMessageId").isNotEmpty())
        .andExpect(jsonPath("$.sessionId").value(sessionId))
        .andExpect(jsonPath("$.providerResponseMeta.reasoningContent").value("先分析选区上下文，再组织最终建议。"))
        .andExpect(jsonPath("$.providerResponseMeta.provider").value("stub-provider"))
        .andExpect(jsonPath("$.providerResponseMeta.model").value("fake-gpt"));
  }

  @Test
  void shouldKeepCancelledWhenLateSuccessArrivesAndRejectCrossScopeReads() throws Exception {
    stubStreamingProvider.enqueueSuccess("晚到成功结果", Duration.ofMillis(2200));
    String documentId = createDocument("cancel-user", "Cancel User");

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult sendResult = mockMvc.perform(
            post("/api/llm/messages")
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"LATE_SUCCESS 请求取消",
                      "selectionSnapshot":{"text":"","emptySelection":true},
                      "headingContext":{"includeHeading":false,"headingId":"","headingText":""},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in_progress"))
        .andReturn();
    String requestId = jsonValue(sendResult, "requestId");

    mockMvc.perform(
            post("/api/llm/requests/{requestId}/cancel", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.errorCode").value("LLM_REQUEST_CANCELLED"));

    Thread.sleep(2400L);

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("cancel-user", "Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.assistantText").isEmpty())
        .andExpect(jsonPath("$.assistantMessageId").isNotEmpty())
        .andExpect(jsonPath("$.sessionId").value(sessionId))
        .andExpect(jsonPath("$.errorCode").value("LLM_REQUEST_CANCELLED"));

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("other-user", "Other User"))
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("LLM_SESSION_FORBIDDEN"));
  }

  @Test
  void shouldAllowDisconnectThenFinalStatusLookup() throws Exception {
    stubStreamingProvider.enqueueSuccess("断流后仍落终态", Duration.ofMillis(150));
    String documentId = createDocument("disconnect-user", "Disconnect User");

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("disconnect-user", "Disconnect User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult streamResult = mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("disconnect-user", "Disconnect User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"模拟客户端断流",
                      "selectionSnapshot":{"text":"断流选区","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    Thread.sleep(40L);
    String startedFrame = streamResult.getResponse().getContentAsString();
    String requestId = jsonFieldFromSse(startedFrame, "requestId");
    assertThat(requestId).isNotBlank();

    Thread.sleep(260L);

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("disconnect-user", "Disconnect User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completed"))
        .andExpect(jsonPath("$.assistantText").value("断流后仍落终态"))
        .andExpect(jsonPath("$.assistantMessageId").isNotEmpty())
        .andExpect(jsonPath("$.sessionId").value(sessionId));
  }

  @Test
  void shouldStreamAssistantErrorAndPersistFailedStatus() throws Exception {
    stubStreamingProvider.enqueueBadRequest();
    String documentId = createDocument("failed-user", "Failed User");

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("failed-user", "Failed User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult streamResult = mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("failed-user", "Failed User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"模拟 provider 失败",
                      "selectionSnapshot":{"text":"失败选区","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    Thread.sleep(120L);
    String streamBody = streamResult.getResponse().getContentAsString();
    assertThat(streamBody).contains("event:request-started");
    assertThat(streamBody).contains("event:assistant-error");

    String requestId = jsonFieldFromSse(streamBody, "requestId");
    assertThat(requestId).isNotBlank();

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("failed-user", "Failed User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("failed"))
        .andExpect(jsonPath("$.assistantText").isEmpty())
        .andExpect(jsonPath("$.errorCode").value("LLM_PROVIDER_BAD_REQUEST"));
  }

  @Test
  void shouldPreservePartialReasoningAndAssistantTextWhenProviderFailsAfterChunks() throws Exception {
    stubStreamingProvider.enqueueFailureAfterPartial("partial-failure");
    String documentId = createDocument("partial-failed-user", "Partial Failed User");

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("partial-failed-user", "Partial Failed User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult streamResult = mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("partial-failed-user", "Partial Failed User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"模拟 provider 中途失败",
                      "selectionSnapshot":{"text":"失败选区","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    Thread.sleep(160L);
    String streamBody = streamResult.getResponse().getContentAsString();
    assertThat(streamBody).contains("event:reasoning-delta");
    assertThat(streamBody).contains("event:assistant-delta");
    assertThat(streamBody).contains("event:assistant-error");

    String requestId = jsonFieldFromSse(streamBody, "requestId");
    assertThat(requestId).isNotBlank();

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("partial-failed-user", "Partial Failed User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("failed"))
        .andExpect(jsonPath("$.assistantText").value("partial"))
        .andExpect(jsonPath("$.providerResponseMeta.reasoningContent").value("先分析选区上下文，"))
        .andExpect(jsonPath("$.errorCode").value("LLM_PROVIDER_UPSTREAM_ERROR"));
  }

  @Test
  void shouldPreservePartialReasoningAndAssistantTextWhenCancelledAfterChunks() throws Exception {
    stubStreamingProvider.enqueuePartialThenSlowTerminal("partial-final", Duration.ofMillis(350));
    String documentId = createDocument("partial-cancel-user", "Partial Cancel User");

    MvcResult createSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("partial-cancel-user", "Partial Cancel User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String sessionId = jsonValue(createSessionResult, "sessionId");

    MvcResult streamResult = mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("partial-cancel-user", "Partial Cancel User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"模拟部分输出后取消",
                      "selectionSnapshot":{"text":"取消选区","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, sessionId))
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    Thread.sleep(90L);
    String startedFrame = streamResult.getResponse().getContentAsString();
    assertThat(startedFrame).contains("event:assistant-delta");
    String requestId = jsonFieldFromSse(startedFrame, "requestId");
    assertThat(requestId).isNotBlank();

    mockMvc.perform(
            post("/api/llm/requests/{requestId}/cancel", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("partial-cancel-user", "Partial Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));

    Thread.sleep(520L);

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("partial-cancel-user", "Partial Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.assistantText").value("partia"))
        .andExpect(jsonPath("$.providerResponseMeta.reasoningContent").value("先分析选区上下文，"))
        .andExpect(jsonPath("$.errorCode").value("LLM_REQUEST_CANCELLED"));
  }

  private String createDocument(String actorUser, String actorName) throws Exception {
    MvcResult result = mockMvc.perform(
            post("/api/documents")
                .headers(TestAccessHeaders.headers(actorUser, actorName))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"llm-flow.docx"}
                    """)
        )
        .andExpect(status().isOk())
        .andReturn();
    return jsonValue(result, "documentId");
  }

  private String jsonValue(MvcResult result, String key) throws Exception {
    String body = result.getResponse().getContentAsString();
    String needle = "\"" + key + "\":\"";
    int start = body.indexOf(needle);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + needle.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }

  private String jsonFieldFromSse(String body, String key) {
    String needle = "\"" + key + "\":\"";
    int start = body.indexOf(needle);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + needle.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }

  static final class StubStreamingProvider implements SpringAiLlmProvider {

    private final Queue<Scenario> scenarios = new ConcurrentLinkedQueue<>();

    void enqueueSuccess(String assistantText, Duration delay) {
      scenarios.add(new Scenario(assistantText, delay, Duration.ZERO, false, false));
    }

    void enqueuePartialThenSlowTerminal(String assistantText, Duration terminalDelay) {
      scenarios.add(new Scenario(assistantText, Duration.ZERO, terminalDelay, false, false));
    }

    void enqueueFailureAfterPartial(String assistantText) {
      scenarios.add(new Scenario(assistantText, Duration.ZERO, Duration.ZERO, false, true));
    }

    void enqueueBadRequest() {
      scenarios.add(new Scenario("", Duration.ZERO, Duration.ZERO, true, false));
    }

    void reset() {
      scenarios.clear();
    }

    @Override
    public String providerName() {
      return "stub-provider";
    }

    @Override
    public Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request) {
      Scenario scenario = scenarios.poll();
      if (scenario == null) {
        scenario = new Scenario("默认回复", Duration.ZERO, Duration.ZERO, false, false);
      }
      if (scenario.badRequest) {
        return Flux.error(new LlmApiException(
            LlmErrorCodes.LLM_PROVIDER_BAD_REQUEST,
            HttpStatus.BAD_REQUEST,
            "模型请求参数无效。"
        ));
      }
      String assistantText = scenario.assistantText;
      Map<String, Object> usage = new LinkedHashMap<>();
      usage.put("promptTokens", 16);
      usage.put("completionTokens", 24);
      usage.put("totalTokens", 40);
      String first = assistantText.substring(0, Math.min(assistantText.length(), Math.max(1, assistantText.length() / 2)));
      String second = assistantText.substring(first.length());
      Flux<SpringAiProviderChunk> firstChunk = Flux.just(new SpringAiProviderChunk(
              first,
              "provider-request-1",
              null,
              null,
              Map.of(
                  "provider", request.providerName(),
                  "model", request.model(),
                  "reasoningContent", "先分析选区上下文，"
              )
          )).delayElements(scenario.firstDelay);
      if (scenario.failAfterPartial) {
        return Flux.concat(
            firstChunk,
            Flux.error(new LlmApiException(
                LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR,
                HttpStatus.BAD_GATEWAY,
                "模型流式响应中断。"
            ))
        );
      }
      return Flux.concat(
          firstChunk,
          Flux.just(new SpringAiProviderChunk(
              second,
              "provider-request-1",
              new LlmProviderUsage(16, 24, 40),
              "stop",
              Map.of(
                  "provider", request.providerName(),
                  "model", request.model(),
                  "created", 1711000000,
                  "reasoningContent", "再组织最终建议。",
                  "usage", usage
              )
          )).delayElements(scenario.secondDelay)
      );
    }

    @Override
    public boolean supportsUpstreamCancel() {
      return false;
    }

    @Override
    public void cancelRequest(String providerRequestId) {
    }

    private record Scenario(
        String assistantText,
        Duration firstDelay,
        Duration secondDelay,
        boolean badRequest,
        boolean failAfterPartial
    ) {
    }
  }
}
