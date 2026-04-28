package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.service.llm.LlmApiException;
import com.earmo.onlyoffice.integration.service.llm.LlmErrorCodes;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderMessage;
import com.earmo.onlyoffice.integration.service.llm.LlmProviderUsage;
import com.earmo.onlyoffice.integration.service.llm.LlmRuntimeRequest;
import com.earmo.onlyoffice.integration.service.llm.SpringAiLlmProvider;
import com.earmo.onlyoffice.integration.service.llm.SpringAiProviderChunk;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    String streamBody = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(streamBody).contains("event:request-started");
    assertThat(streamBody).contains("event:reasoning-delta");
    assertThat(streamBody).contains("event:assistant-delta");
    assertThat(streamBody).contains("event:assistant-completed");
    assertThat(streamBody.indexOf("event:reasoning-delta")).isLessThan(streamBody.indexOf("event:assistant-delta"));
    assertThat(streamBody).contains("\"reasoningText\":");
    assertThat(streamBody).contains("\"sessionTitle\":\"STREAM 首轮问题\"");

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

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("llm-user", "LLM User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("STREAM 首轮问题"));
  }

  @Test
  void shouldListSessionsByLastConversationTime() throws Exception {
    stubStreamingProvider.enqueueSuccess("第一条会话回复", Duration.ofMillis(10));
    String documentId = createDocument("sort-user", "Sort User");

    MvcResult firstSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("sort-user", "Sort User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s","title":"较早会话"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String firstSessionId = jsonValue(firstSessionResult, "sessionId");

    Thread.sleep(20L);

    MvcResult secondSessionResult = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers("sort-user", "Sort User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s","title":"较新会话"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    String secondSessionId = jsonValue(secondSessionResult, "sessionId");

    mockMvc.perform(
            get("/api/llm/sessions")
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("sort-user", "Sort User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sessionId").value(secondSessionId))
        .andExpect(jsonPath("$[0].lastConversationTime").isNotEmpty());

    mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("sort-user", "Sort User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"更新较早会话的最后对话时间",
                      "selectionSnapshot":{"text":"","emptySelection":true},
                      "headingContext":{"includeHeading":false,"headingId":"","headingText":""},
                      "retryConfirmed":false
                    }
                    """.formatted(documentId, firstSessionId))
        )
        .andExpect(request().asyncStarted())
        .andReturn();

    Thread.sleep(120L);

    mockMvc.perform(
            get("/api/llm/sessions")
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("sort-user", "Sort User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sessionId").value(firstSessionId))
        .andExpect(jsonPath("$[1].sessionId").value(secondSessionId));
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
    String startedFrame = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
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
    String streamBody = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
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
    String streamBody = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
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
    String startedFrame = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
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

  @Test
  void flywayMigratesExistingAssistantRowsToVariantZero() {
    DataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:llm-variant-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "sa",
        ""
    );
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("9"))
        .load()
        .migrate();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.update("""
        insert into document_llm_message (
          message_id, session_id, document_id, tenant_id, actor_user, role,
          assistant_text, status, provider_usage_json, provider_meta_json,
          finish_reason, error_code, created_time
        )
        values
          ('assistant-completed-v0', 'session-v0', 'doc-v0', 'tenant-v0', 'user-v0', 'assistant',
           '历史完成正文', 'completed', '{"totalTokens":3}', '{"reasoningContent":"历史思考"}',
           'stop', null, timestamp '2026-04-28 00:00:00'),
          ('assistant-pending-v0', 'session-v0', 'doc-v0', 'tenant-v0', 'user-v0', 'assistant',
           null, 'pending', null, '{"provider":"stub-provider"}',
           null, null, timestamp '2026-04-28 00:00:01'),
          ('user-message-v0', 'session-v0', 'doc-v0', 'tenant-v0', 'user-v0', 'user',
           null, 'completed', null, null, null, null, timestamp '2026-04-28 00:00:02')
        """);

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    Integer variantCount = jdbcTemplate.queryForObject(
        "select count(*) from document_llm_message_variant",
        Integer.class
    );
    Integer completedActiveIndex = jdbcTemplate.queryForObject(
        "select active_variant_index from document_llm_message where message_id = 'assistant-completed-v0'",
        Integer.class
    );
    Integer userActiveIndexCount = jdbcTemplate.queryForObject(
        "select count(*) from document_llm_message where message_id = 'user-message-v0' and active_variant_index is null",
        Integer.class
    );
    String pendingAssistantText = jdbcTemplate.queryForObject(
        "select assistant_text from document_llm_message_variant where message_id = 'assistant-pending-v0'",
        String.class
    );

    assertThat(variantCount).isEqualTo(2);
    assertThat(completedActiveIndex).isZero();
    assertThat(userActiveIndexCount).isEqualTo(1);
    assertThat(pendingAssistantText).isNull();
  }

  @Test
  void regenerateCreatesVariantWithoutNewConversationEntry() throws Exception {
    stubStreamingProvider.enqueueSuccess("首轮回复", Duration.ofMillis(10));
    stubStreamingProvider.enqueueSuccess("重新生成回复", Duration.ofMillis(10));
    String documentId = createDocument("regen-user", "Regen User");
    String sessionId = createSession(documentId, "regen-user", "Regen User");

    MvcResult firstResult = streamMessage(documentId, sessionId, "regen-user", "Regen User", "原始问题", null);
    Thread.sleep(120L);
    String firstBody = firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String assistantMessageId = jsonFieldFromSse(firstBody, "assistantMessageId");
    assertThat(jsonFieldFromSse(firstBody, "variantIndex")).isEqualTo("0");

    MvcResult regenerateResult = streamMessage(documentId, sessionId, "regen-user", "Regen User", "原始问题", assistantMessageId);
    Thread.sleep(120L);
    String regenerateBody = regenerateResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(jsonFieldFromSse(regenerateBody, "assistantMessageId")).isEqualTo(assistantMessageId);
    assertThat(jsonFieldFromSse(regenerateBody, "variantIndex")).isEqualTo("1");

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("regen-user", "Regen User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[1].messageId").value(assistantMessageId))
        .andExpect(jsonPath("$.messages[1].variants.length()").value(2))
        .andExpect(jsonPath("$.messages[1].variants[0].variantIndex").value(0))
        .andExpect(jsonPath("$.messages[1].variants[1].variantIndex").value(1));
  }

  @Test
  void regenerateRejectsAssistantFromAnotherSession() throws Exception {
    stubStreamingProvider.enqueueSuccess("第一会话回复", Duration.ofMillis(10));
    String documentId = createDocument("regen-scope-user", "Regen Scope User");
    String firstSessionId = createSession(documentId, "regen-scope-user", "Regen Scope User");
    String secondSessionId = createSession(documentId, "regen-scope-user", "Regen Scope User");

    MvcResult firstResult = streamMessage(documentId, firstSessionId, "regen-scope-user", "Regen Scope User", "第一会话问题", null);
    Thread.sleep(120L);
    String assistantMessageId = jsonFieldFromSse(firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "assistantMessageId");

    mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers("regen-scope-user", "Regen Scope User"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"错误 session regenerate",
                      "selectionSnapshot":{"text":"","emptySelection":true},
                      "headingContext":{"includeHeading":false,"headingId":"","headingText":""},
                      "retryConfirmed":true,
                      "regenerateAssistantMessageId":"%s"
                    }
                    """.formatted(documentId, secondSessionId, assistantMessageId))
        )
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentRegenerateCreatesDistinctVariantIndexes() throws Exception {
    stubStreamingProvider.enqueueSuccess("首轮回复", Duration.ofMillis(10));
    stubStreamingProvider.enqueueSuccess("重新生成 A", Duration.ofMillis(60));
    stubStreamingProvider.enqueueSuccess("重新生成 B", Duration.ofMillis(60));
    String documentId = createDocument("regen-race-user", "Regen Race User");
    String sessionId = createSession(documentId, "regen-race-user", "Regen Race User");

    MvcResult firstResult = streamMessage(documentId, sessionId, "regen-race-user", "Regen Race User", "并发 regenerate 问题", null);
    Thread.sleep(120L);
    String assistantMessageId = jsonFieldFromSse(firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "assistantMessageId");

    MvcResult firstRegenerate = streamMessage(documentId, sessionId, "regen-race-user", "Regen Race User", "并发 regenerate 问题", assistantMessageId);
    MvcResult secondRegenerate = streamMessage(documentId, sessionId, "regen-race-user", "Regen Race User", "并发 regenerate 问题", assistantMessageId);
    Thread.sleep(180L);
    String firstBody = firstRegenerate.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String secondBody = secondRegenerate.getResponse().getContentAsString(StandardCharsets.UTF_8);

    assertThat(firstBody).contains("event:request-started");
    assertThat(secondBody).contains("event:request-started");
    assertThat(jsonFieldFromSse(firstBody, "variantIndex")).isNotEqualTo(jsonFieldFromSse(secondBody, "variantIndex"));

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("regen-race-user", "Regen Race User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[1].variants.length()").value(3));
  }

  @Test
  void streamEventsExposeVariantIdentity() throws Exception {
    stubStreamingProvider.enqueueSuccess("带 variant 的回复", Duration.ofMillis(10));
    String documentId = createDocument("variant-event-user", "Variant Event User");
    String sessionId = createSession(documentId, "variant-event-user", "Variant Event User");

    MvcResult result = streamMessage(documentId, sessionId, "variant-event-user", "Variant Event User", "检查 stream variant identity", null);
    Thread.sleep(120L);
    String streamBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String requestId = jsonFieldFromSse(streamBody, "requestId");
    String variantId = jsonFieldFromSse(streamBody, "variantId");

    assertThat(streamBody).contains("event:request-started");
    assertThat(streamBody).contains("event:assistant-delta");
    assertThat(streamBody).contains("event:reasoning-delta");
    assertThat(streamBody).contains("event:assistant-meta");
    assertThat(streamBody).contains("event:assistant-completed");
    assertThat(streamBody).contains("\"variantId\":\"" + variantId + "\"");
    assertThat(streamBody).contains("\"variantIndex\":0");
    assertThat(streamBody).contains("\"activeVariantIndex\":0");

    mockMvc.perform(
            get("/api/llm/requests/{requestId}", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("variant-event-user", "Variant Event User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.variantId").value(variantId))
        .andExpect(jsonPath("$.variantIndex").value(0))
        .andExpect(jsonPath("$.activeVariantIndex").value(0));
  }

  @Test
  void cancelledRegenerateKeepsPreviousCompletedVariant() throws Exception {
    stubStreamingProvider.enqueueSuccess("可用旧版本", Duration.ofMillis(10));
    stubStreamingProvider.enqueuePartialThenSlowTerminal("被取消的新版本", Duration.ofMillis(350));
    String documentId = createDocument("variant-cancel-user", "Variant Cancel User");
    String sessionId = createSession(documentId, "variant-cancel-user", "Variant Cancel User");

    MvcResult firstResult = streamMessage(documentId, sessionId, "variant-cancel-user", "Variant Cancel User", "取消 regenerate 问题", null);
    Thread.sleep(120L);
    String assistantMessageId = jsonFieldFromSse(firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "assistantMessageId");

    MvcResult regenerateResult = streamMessage(documentId, sessionId, "variant-cancel-user", "Variant Cancel User", "取消 regenerate 问题", assistantMessageId);
    Thread.sleep(90L);
    String regenerateBody = regenerateResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String requestId = jsonFieldFromSse(regenerateBody, "requestId");
    assertThat(jsonFieldFromSse(regenerateBody, "variantIndex")).isEqualTo("1");

    mockMvc.perform(
            post("/api/llm/requests/{requestId}/cancel", requestId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("variant-cancel-user", "Variant Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.variantIndex").value(1));

    Thread.sleep(420L);

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("variant-cancel-user", "Variant Cancel User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[1].activeVariantIndex").value(0))
        .andExpect(jsonPath("$.messages[1].assistantText").value("可用旧版本"))
        .andExpect(jsonPath("$.messages[1].variants[1].status").value("cancelled"))
        .andExpect(jsonPath("$.messages[1].variants[1].assistantText").value("被取消"));
  }

  @Test
  void switchActiveVariantPersistsScopedIndex() throws Exception {
    stubStreamingProvider.enqueueSuccess("版本零", Duration.ofMillis(10));
    stubStreamingProvider.enqueueSuccess("版本一", Duration.ofMillis(10));
    String documentId = createDocument("variant-switch-user", "Variant Switch User");
    String sessionId = createSession(documentId, "variant-switch-user", "Variant Switch User");

    MvcResult firstResult = streamMessage(documentId, sessionId, "variant-switch-user", "Variant Switch User", "切换版本问题", null);
    Thread.sleep(120L);
    String assistantMessageId = jsonFieldFromSse(firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "assistantMessageId");
    streamMessage(documentId, sessionId, "variant-switch-user", "Variant Switch User", "切换版本问题", assistantMessageId);
    Thread.sleep(120L);

    mockMvc.perform(
            put("/api/llm/messages/{messageId}/active-variant", assistantMessageId)
                .headers(TestAccessHeaders.headers("variant-switch-user", "Variant Switch User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s","sessionId":"%s","variantIndex":0}
                    """.formatted(documentId, sessionId))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeVariantIndex").value(0))
        .andExpect(jsonPath("$.assistantText").value("版本零"));

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("variant-switch-user", "Variant Switch User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[1].activeVariantIndex").value(0))
        .andExpect(jsonPath("$.messages[1].assistantText").value("版本零"));
  }

  @Test
  void terminalCompletedDoesNotOverrideUserActiveSwitch() throws Exception {
    stubStreamingProvider.enqueueSuccess("稳定旧版本", Duration.ofMillis(10));
    stubStreamingProvider.enqueueSuccess("晚到新版本", Duration.ofMillis(260));
    String documentId = createDocument("variant-race-user", "Variant Race User");
    String sessionId = createSession(documentId, "variant-race-user", "Variant Race User");

    MvcResult firstResult = streamMessage(documentId, sessionId, "variant-race-user", "Variant Race User", "active race 问题", null);
    Thread.sleep(120L);
    String assistantMessageId = jsonFieldFromSse(firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "assistantMessageId");
    MvcResult regenerateResult = streamMessage(documentId, sessionId, "variant-race-user", "Variant Race User", "active race 问题", assistantMessageId);
    Thread.sleep(60L);
    assertThat(jsonFieldFromSse(regenerateResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "variantIndex")).isEqualTo("1");

    mockMvc.perform(
            put("/api/llm/messages/{messageId}/active-variant", assistantMessageId)
                .headers(TestAccessHeaders.headers("variant-race-user", "Variant Race User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s","sessionId":"%s","variantIndex":0}
                    """.formatted(documentId, sessionId))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeVariantIndex").value(0));

    Thread.sleep(340L);

    mockMvc.perform(
            get("/api/llm/sessions/{sessionId}", sessionId)
                .param("documentId", documentId)
                .headers(TestAccessHeaders.headers("variant-race-user", "Variant Race User"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[1].activeVariantIndex").value(0))
        .andExpect(jsonPath("$.messages[1].assistantText").value("稳定旧版本"))
        .andExpect(jsonPath("$.messages[1].variants[1].status").value("completed"));
  }

  @Test
  void promptHistoryUsesOnlyActiveVariant() throws Exception {
    stubStreamingProvider.enqueueSuccess("历史版本零", Duration.ofMillis(10));
    stubStreamingProvider.enqueueSuccess("历史版本一", Duration.ofMillis(10));
    stubStreamingProvider.enqueueSuccess("后续回复", Duration.ofMillis(10));
    String documentId = createDocument("variant-history-user", "Variant History User");
    String sessionId = createSession(documentId, "variant-history-user", "Variant History User");

    MvcResult firstResult = streamMessage(documentId, sessionId, "variant-history-user", "Variant History User", "历史问题", null);
    Thread.sleep(120L);
    String assistantMessageId = jsonFieldFromSse(firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "assistantMessageId");
    streamMessage(documentId, sessionId, "variant-history-user", "Variant History User", "历史问题", assistantMessageId);
    Thread.sleep(120L);

    mockMvc.perform(
            put("/api/llm/messages/{messageId}/active-variant", assistantMessageId)
                .headers(TestAccessHeaders.headers("variant-history-user", "Variant History User"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s","sessionId":"%s","variantIndex":0}
                    """.formatted(documentId, sessionId))
        )
        .andExpect(status().isOk());

    streamMessage(documentId, sessionId, "variant-history-user", "Variant History User", "后续问题", null);
    Thread.sleep(120L);

    LlmRuntimeRequest latestRequest = stubStreamingProvider.observedRequests().getLast();
    String promptText = latestRequest.messages().toString();
    assertThat(promptText).contains("历史版本零");
    assertThat(promptText).doesNotContain("历史版本一");
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

  private String createSession(String documentId, String actorUser, String actorName) throws Exception {
    MvcResult result = mockMvc.perform(
            post("/api/llm/sessions")
                .headers(TestAccessHeaders.headers(actorUser, actorName))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"documentId":"%s"}
                    """.formatted(documentId))
        )
        .andExpect(status().isOk())
        .andReturn();
    return jsonValue(result, "sessionId");
  }

  private MvcResult streamMessage(
      String documentId,
      String sessionId,
      String actorUser,
      String actorName,
      String question,
      String regenerateAssistantMessageId
  ) throws Exception {
    String regenerateField = regenerateAssistantMessageId == null
        ? ""
        : """
                      ,"regenerateAssistantMessageId":"%s"
            """.formatted(regenerateAssistantMessageId);
    return mockMvc.perform(
            post("/api/llm/messages/stream")
                .headers(TestAccessHeaders.headers(actorUser, actorName))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "documentId":"%s",
                      "sessionId":"%s",
                      "provider":"stub-provider",
                      "model":"fake-gpt",
                      "question":"%s",
                      "selectionSnapshot":{"text":"选区内容","emptySelection":false},
                      "headingContext":{"includeHeading":true,"headingId":"heading-1","headingText":"第一章"},
                      "retryConfirmed":%s
                      %s
                    }
                    """.formatted(documentId, sessionId, question, regenerateAssistantMessageId != null, regenerateField))
        )
        .andExpect(request().asyncStarted())
        .andReturn();
  }

  private String jsonValue(MvcResult result, String key) throws Exception {
    String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
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
    if (start >= 0) {
      int valueStart = start + needle.length();
      int valueEnd = body.indexOf('"', valueStart);
      return body.substring(valueStart, valueEnd);
    }
    needle = "\"" + key + "\":";
    start = body.indexOf(needle);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + needle.length();
    int valueEnd = body.indexOf(',', valueStart);
    if (valueEnd < 0) {
      valueEnd = body.indexOf('}', valueStart);
    }
    return body.substring(valueStart, valueEnd);
  }

  static final class StubStreamingProvider implements SpringAiLlmProvider {

    private final Queue<Scenario> scenarios = new ConcurrentLinkedQueue<>();
    private final Queue<LlmRuntimeRequest> observedRequests = new ConcurrentLinkedQueue<>();

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
      observedRequests.clear();
    }

    List<LlmRuntimeRequest> observedRequests() {
      return List.copyOf(observedRequests);
    }

    @Override
    public String providerName() {
      return "stub-provider";
    }

    @Override
    public Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request) {
      observedRequests.add(request);
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
