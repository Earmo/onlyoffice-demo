package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmRequestEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmSessionEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmMessageRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmRequestRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmSessionRepository;
import com.earmo.onlyoffice.integration.model.llm.CreateLlmSessionRequest;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmMessageResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmProviderOptionResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDetailResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionSummaryResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmStreamEventResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmUsageResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Phase 14.2 的会话主服务。
 *
 * <p>这一层负责把“文档上下文 + 会话状态 + provider 流式响应”收口成统一的领域流程：
 * 先落库 user/request/pending assistant，再异步消费 provider stream，最后由服务端统一裁决终态并回写数据库。
 * 前端只消费标准 DTO 和 AI SSE 事件，不直接依赖任意上游 provider 的响应格式。
 */
@Service
@Slf4j
public class LlmConversationService {

  private static final String STATUS_PENDING = "pending";
  private static final String STATUS_IN_PROGRESS = "in_progress";
  private static final String STATUS_COMPLETED = "completed";
  private static final String STATUS_FAILED = "failed";
  private static final String STATUS_CANCELLED = "cancelled";
  private static final String CANCEL_SOURCE_USER = "user";
  private static final String CANCEL_SOURCE_CLIENT_DISCONNECT = "client_disconnect";
  // 流式链路需要给上游 provider timeout 留收口余量，避免“上游已超时但 servlet 先把请求砍掉”。
  private static final long STREAM_TIMEOUT_BUFFER_MILLIS = 30000L;
  private static final long MIN_STREAM_TIMEOUT_MILLIS = 300000L;
  private static final DateTimeFormatter SESSION_TITLE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
      .withZone(ZoneId.of("Asia/Shanghai"));

  private final LlmProperties llmProperties;
  private final SpringAiProviderRegistry providerRegistry;
  private final DocumentLlmSessionRepository documentLlmSessionRepository;
  private final DocumentLlmMessageRepository documentLlmMessageRepository;
  private final DocumentLlmRequestRepository documentLlmRequestRepository;
  private final LlmConversationAccessGuard accessGuard;
  private final LlmRequestExecutionRegistry executionRegistry;
  private final LlmPromptWindowBuilder promptWindowBuilder;
  private final ObjectMapper objectMapper;
  private final Executor llmExecutor;

  /**
   * 注入会话流程所需的仓储、provider 适配器和执行器。
   */
  public LlmConversationService(
      LlmProperties llmProperties,
      SpringAiProviderRegistry providerRegistry,
      DocumentLlmSessionRepository documentLlmSessionRepository,
      DocumentLlmMessageRepository documentLlmMessageRepository,
      DocumentLlmRequestRepository documentLlmRequestRepository,
      LlmConversationAccessGuard accessGuard,
      LlmRequestExecutionRegistry executionRegistry,
      LlmPromptWindowBuilder promptWindowBuilder,
      ObjectMapper objectMapper,
      @Qualifier("llmExecutor") Executor llmExecutor
  ) {
    this.llmProperties = llmProperties;
    this.providerRegistry = providerRegistry;
    this.documentLlmSessionRepository = documentLlmSessionRepository;
    this.documentLlmMessageRepository = documentLlmMessageRepository;
    this.documentLlmRequestRepository = documentLlmRequestRepository;
    this.accessGuard = accessGuard;
    this.executionRegistry = executionRegistry;
    this.promptWindowBuilder = promptWindowBuilder;
    this.objectMapper = objectMapper;
    this.llmExecutor = llmExecutor;
  }

  /**
   * 返回当前文档的 AI 能力描述。
   *
   * <p>这里给前端的是“逻辑 provider 是否可用”的结论，
   * 结论同时依赖功能开关、provider 配置和 provider 实现是否已注册。
   */
  public LlmCapabilityResponse getCapability(String documentId, AccessContext accessContext) {
    // capability 面向前端暴露的是“逻辑 provider”能力，但真正可用还要满足两层条件：
    // 1. llm.providers.<name> 已配置且启用
    // 2. 该逻辑 provider 能映射到一个已注册的 SpringAiLlmProvider 实现
    List<LlmProviderOptionResponse> availableProviders = buildAvailableProviderOptions();
    String defaultProvider = llmProperties.resolveDefaultProvider();
    String defaultModel = llmProperties.resolveDefaultModel();
    boolean hasDefaultProvider = llmProperties.hasUsableProvider(defaultProvider) && findSpringAiProvider(defaultProvider).isPresent();
    boolean supportsUpstreamCancel = availableProviders.stream()
        .filter(option -> option.provider().equals(defaultProvider))
        .findFirst()
        .map(LlmProviderOptionResponse::supportsUpstreamCancel)
        .orElse(false);
    boolean streamMode = availableProviders.stream()
        .filter(option -> option.provider().equals(defaultProvider))
        .findFirst()
        .map(LlmProviderOptionResponse::streamEnabled)
        .orElse(false);

    if (!llmProperties.isFeatureEnabled() || !llmProperties.isEnabled()) {
      return new LlmCapabilityResponse(
          documentId,
          false,
          LlmErrorCodes.LLM_DISABLED,
          defaultProvider,
          defaultModel,
          supportsUpstreamCancel,
          streamMode,
          defaultProvider,
          defaultModel,
          availableProviders
      );
    }
    if (!hasAnyConfiguredProvider() || !hasDefaultProvider) {
      return new LlmCapabilityResponse(
          documentId,
          false,
          LlmErrorCodes.LLM_UNAVAILABLE,
          defaultProvider,
          defaultModel,
          supportsUpstreamCancel,
          streamMode,
          defaultProvider,
          defaultModel,
          availableProviders
      );
    }
    return new LlmCapabilityResponse(
        documentId,
        true,
        null,
        defaultProvider,
        defaultModel,
        supportsUpstreamCancel,
        streamMode,
        defaultProvider,
        defaultModel,
        availableProviders
    );
  }

  /**
   * 列出当前文档下当前用户可见的 AI 会话摘要。
   */
  public List<LlmSessionSummaryResponse> listSessions(String documentId, AccessContext accessContext) {
    return documentLlmSessionRepository.findSessionsByScope(documentId, accessContext.tenantId(), accessContext.actorUser(), 50)
        .stream()
        .map(this::toSessionSummary)
        .toList();
  }

  /**
   * 新建会话并在超额时归档旧会话。
   *
   * <p>处理步骤：
   * 1. 生成会话主键和默认标题；
   * 2. 初始化最近快照为空；
   * 3. 写库；
   * 4. 按配置归档超出上限的旧会话。
   */
  public LlmSessionDetailResponse createSession(CreateLlmSessionRequest request, AccessContext accessContext) {
    Instant now = Instant.now();
    DocumentLlmSessionEntity entity = new DocumentLlmSessionEntity();
    entity.setSessionId(UUID.randomUUID().toString());
    entity.setDocumentId(request.documentId());
    entity.setTenantId(accessContext.tenantId());
    entity.setActorUser(accessContext.actorUser());
    entity.setTitle(request.title() == null || request.title().isBlank() ? "新会话 " + SESSION_TITLE_FORMATTER.format(now) : request.title().trim());
    entity.setLastSnapshotText("");
    entity.setLastSnapshotIsEmpty(true);
    entity.setCreatedTime(now);
    entity.setUpdatedTime(now);
    documentLlmSessionRepository.insert(entity);
    documentLlmSessionRepository.archiveOverflowSessions(
        request.documentId(),
        accessContext.tenantId(),
        accessContext.actorUser(),
        llmProperties.getSession().getMaxSessionsPerDocument(),
        now
    );
    return toSessionDetail(entity, List.of());
  }

  /**
   * 获取单个会话详情及消息列表。
   */
  public LlmSessionDetailResponse getSession(String documentId, String sessionId, AccessContext accessContext) {
    DocumentLlmSessionEntity session = accessGuard.requireSession(documentId, sessionId, accessContext);
    List<LlmMessageResponse> messages = documentLlmMessageRepository.findMessagesBySessionScope(
            sessionId,
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser(),
            llmProperties.getSession().getMaxMessagesPerSession()
        )
        .stream()
        .map(this::toMessageResponse)
        .toList();
    return toSessionDetail(session, messages);
  }

  /**
   * 兼容旧同步接口发送消息。
   *
   * <p>内部仍然使用流式执行链路，只是在当前线程短暂等待一段时间，
   * 若窗口内未完成则直接返回 `in_progress`。
   */
  public LlmRequestStatusResponse sendMessage(SendLlmMessageRequest request, AccessContext accessContext) {
    // 兼容旧同步接口：内部仍走同一套流式执行逻辑，只做一个很短的同步等待窗口。
    // 若窗口内未完成，就返回 in_progress，由客户端继续通过 request 查询最终态。
    PreparedRequest preparedRequest = beginRequest(request, accessContext);
    executionRegistry.register(preparedRequest.requestEntity().getRequestId(), preparedRequest.runtimeSelection().provider());
    CompletableFuture<Void> execution = CompletableFuture.runAsync(
        () -> executeProviderStream(preparedRequest, StreamEventSink.noop()),
        llmExecutor
    );
    try {
      execution.get(llmProperties.getRequestSyncWaitMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ignored) {
      // 兼容接口仍返回 in_progress，供旧客户端最终态回查。
    } catch (Exception ignored) {
      // executeProviderStream 已经收口失败态。
    }
    return getRequest(request.documentId(), preparedRequest.requestEntity().getRequestId(), accessContext);
  }

  /**
   * 以 SSE 方式发送消息并流式返回模型输出。
   */
  public SseEmitter streamMessage(SendLlmMessageRequest request, AccessContext accessContext) {
    PreparedRequest preparedRequest = beginRequest(request, accessContext);
    executionRegistry.register(preparedRequest.requestEntity().getRequestId(), preparedRequest.runtimeSelection().provider());
    long streamTimeoutMillis = resolveStreamTimeoutMillis(preparedRequest.runtimeSelection().timeoutMillis());
    SseEmitter emitter = new SseEmitter(streamTimeoutMillis);
    EmitterStreamEventSink sink = new EmitterStreamEventSink(
        emitter,
        () -> cancelPreparedRequest(preparedRequest, CANCEL_SOURCE_CLIENT_DISCONNECT)
    );
    // request-started 必须先发，前端据此拿到 requestId / assistantMessageId，
    // 后续 delta、取消和断流回查都依赖这两个标识。
    sink.send("request-started", startedEvent(preparedRequest));
    log.info(
        "Opened llm stream, requestId={}, provider={}, model={}, providerTimeoutMs={}, streamTimeoutMs={}",
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        preparedRequest.runtimeSelection().timeoutMillis(),
        streamTimeoutMillis
    );
    CompletableFuture.runAsync(() -> executeProviderStream(preparedRequest, sink), llmExecutor);
    return emitter;
  }

  /**
   * 查询单个请求的当前状态。
   */
  public LlmRequestStatusResponse getRequest(String documentId, String requestId, AccessContext accessContext) {
    DocumentLlmRequestEntity requestEntity = accessGuard.requireRequest(documentId, requestId, accessContext);
    DocumentLlmMessageEntity assistantMessage = documentLlmMessageRepository.findMessageByScope(
            requestEntity.getAssistantMessageId(),
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser()
        )
        .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "assistant 消息不存在。"));
    return toRequestStatusResponse(requestEntity, assistantMessage);
  }

  /**
   * 取消一个仍在执行中的请求。
   *
   * <p>处理步骤：
   * 1. 校验请求访问权限；
   * 2. 只有 `in_progress` 请求允许取消；
   * 3. 先抢占内存终态，再持久化取消标记；
   * 4. 把 assistant 占位消息改成 cancelled。
   */
  public LlmRequestStatusResponse cancelRequest(String documentId, String requestId, AccessContext accessContext) {
    DocumentLlmRequestEntity requestEntity = accessGuard.requireRequest(documentId, requestId, accessContext);
    if (!STATUS_IN_PROGRESS.equals(requestEntity.getStatus())) {
      return getRequest(documentId, requestId, accessContext);
    }
    boolean cancelClaimed = executionRegistry.tryMarkCancelled(requestId);
    if (!cancelClaimed && executionRegistry.hasExecution(requestId)) {
      return getRequest(documentId, requestId, accessContext);
    }
    DocumentLlmMessageEntity assistantMessage = documentLlmMessageRepository.findMessageByScope(
            requestEntity.getAssistantMessageId(),
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser()
        )
        .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "assistant 消息不存在。"));
    persistCancelledRequest(requestEntity, assistantMessage, accessContext, CANCEL_SOURCE_USER);
    return toRequestStatusResponse(requestEntity, assistantMessage);
  }

  /**
   * 创建一次完整的发送请求上下文。
   *
   * <p>这是所有发送路径的共同入口，负责把用户输入折叠成：
   * 会话更新、user message、pending assistant、request 记录和最终运行时 prompt。
   */
  private PreparedRequest beginRequest(SendLlmMessageRequest request, AccessContext accessContext) {
    requireLlmEnabled();
    RuntimeSelection runtimeSelection = resolveSelection(request);
    DocumentLlmSessionEntity session = accessGuard.requireSession(request.documentId(), request.sessionId(), accessContext);
    Instant now = Instant.now();

    // 固定的建单顺序：
    // 1. user message 先落库，保证问题上下文可追溯
    // 2. assistant message 以 pending 预插，占住最终结果位置
    // 3. request 记录独立保存执行态，便于取消、回查和终态仲裁
    DocumentLlmMessageEntity userMessage = new DocumentLlmMessageEntity();
    userMessage.setMessageId(UUID.randomUUID().toString());
    userMessage.setSessionId(session.getSessionId());
    userMessage.setDocumentId(request.documentId());
    userMessage.setTenantId(accessContext.tenantId());
    userMessage.setActorUser(accessContext.actorUser());
    userMessage.setRole("user");
    userMessage.setMessageText(request.question());
    userMessage.setSnapshotText(request.selectionSnapshot().text());
    userMessage.setSnapshotIsEmpty(request.selectionSnapshot().emptySelection());
    userMessage.setHeadingId(request.headingContext().headingId());
    userMessage.setHeadingText(request.headingContext().headingText());
    userMessage.setIncludeHeading(request.headingContext().includeHeading());
    userMessage.setStatus(STATUS_COMPLETED);
    userMessage.setCreatedTime(now);
    documentLlmMessageRepository.insert(userMessage);

    DocumentLlmMessageEntity assistantMessage = new DocumentLlmMessageEntity();
    assistantMessage.setMessageId(UUID.randomUUID().toString());
    assistantMessage.setSessionId(session.getSessionId());
    assistantMessage.setDocumentId(request.documentId());
    assistantMessage.setTenantId(accessContext.tenantId());
    assistantMessage.setActorUser(accessContext.actorUser());
    assistantMessage.setRole("assistant");
    assistantMessage.setSnapshotText(request.selectionSnapshot().text());
    assistantMessage.setSnapshotIsEmpty(request.selectionSnapshot().emptySelection());
    assistantMessage.setHeadingId(request.headingContext().headingId());
    assistantMessage.setHeadingText(request.headingContext().headingText());
    assistantMessage.setIncludeHeading(request.headingContext().includeHeading());
    assistantMessage.setStatus(STATUS_PENDING);
    assistantMessage.setProviderMetaJson(writeJson(initialProviderMeta(runtimeSelection)));
    assistantMessage.setCreatedTime(now);
    documentLlmMessageRepository.insert(assistantMessage);

    // request 记录只承载执行态，不直接保存文本内容。
    DocumentLlmRequestEntity requestEntity = new DocumentLlmRequestEntity();
    requestEntity.setRequestId(UUID.randomUUID().toString());
    requestEntity.setSessionId(session.getSessionId());
    requestEntity.setDocumentId(request.documentId());
    requestEntity.setTenantId(accessContext.tenantId());
    requestEntity.setActorUser(accessContext.actorUser());
    requestEntity.setUserMessageId(userMessage.getMessageId());
    requestEntity.setAssistantMessageId(assistantMessage.getMessageId());
    requestEntity.setStatus(STATUS_IN_PROGRESS);
    requestEntity.setCancelRequested(false);
    requestEntity.setStartedTime(now);
    documentLlmRequestRepository.insert(requestEntity);

    // 会话表只保留“最近一次发送”的摘要，便于列表页和详情页快速展示。
    session.setLastSnapshotText(request.selectionSnapshot().text());
    session.setLastSnapshotIsEmpty(request.selectionSnapshot().emptySelection());
    session.setLastHeadingId(request.headingContext().headingId());
    session.setLastHeadingText(request.headingContext().headingText());
    session.setUpdatedTime(now);
    documentLlmSessionRepository.update(session);

    List<DocumentLlmMessageEntity> history = new ArrayList<>(documentLlmMessageRepository.findMessagesBySessionScope(
        session.getSessionId(),
        session.getDocumentId(),
        accessContext.tenantId(),
        accessContext.actorUser(),
        llmProperties.getSession().getMaxMessagesPerSession()
    ));
    // 当前这条 assistant 还是 pending 占位，不能参与 prompt window；
    // 其他未完成消息也一并剔除，避免把半截回复再次送回模型。
    history.removeIf(message -> message.getMessageId().equals(assistantMessage.getMessageId()) || STATUS_PENDING.equals(message.getStatus()));

    LlmRuntimeRequest runtimeRequest = new LlmRuntimeRequest(
        runtimeSelection.providerName(),
        runtimeSelection.baseUrl(),
        runtimeSelection.apiKey(),
        runtimeSelection.model(),
        runtimeSelection.timeoutMillis(),
        promptWindowBuilder.buildMessages(
            llmProperties,
            history,
            request.question(),
            request.selectionSnapshot().text(),
            request.selectionSnapshot().emptySelection(),
            llmProperties.isAllowHeadingContext() && request.headingContext().includeHeading(),
            request.headingContext().headingText()
        )
    );

    return new PreparedRequest(
        session,
        userMessage,
        assistantMessage,
        requestEntity,
        request,
        accessContext,
        runtimeSelection,
        runtimeRequest
    );
  }

  /**
   * 执行 provider 流式请求并收口最终状态。
   *
   * <p>处理步骤：
   * 1. 注册运行态；
   * 2. 消费 provider stream，把 chunk 累加到内存；
   * 3. 竞争完成终态；
   * 4. 一次性回写 request / assistant / session；
   * 5. 推送最终 SSE 事件并清理运行态。
   */
  private void executeProviderStream(PreparedRequest preparedRequest, StreamEventSink sink) {
    String requestId = preparedRequest.requestEntity().getRequestId();
    StreamAccumulator accumulator = new StreamAccumulator(preparedRequest.runtimeSelection().providerName(), preparedRequest.runtimeSelection().model());
    try {
      if (executionRegistry.isCancelled(requestId)) {
        sink.send("assistant-cancelled", cancelledEvent(preparedRequest));
        sink.complete();
        return;
      }
      // 流式增量只进内存累加器和 SSE，不在每个 chunk 时落库。
      // 这样可以避免高频 update 把数据库变成 token 级日志，同时保证 terminal path 一次性写入最终 assistantText。
      CountDownLatch streamFinished = new CountDownLatch(1);
      AtomicReference<Throwable> streamFailure = new AtomicReference<>();
      Disposable subscription = preparedRequest.runtimeSelection().provider().stream(preparedRequest.runtimeRequest())
          .timeout(Duration.ofMillis(preparedRequest.runtimeSelection().timeoutMillis()))
          .doFinally(signalType -> streamFinished.countDown())
          .subscribe(
              chunk -> handleProviderChunk(preparedRequest, sink, accumulator, chunk),
              streamFailure::set
          );
      executionRegistry.attachStreamSubscription(requestId, subscription);
      streamFinished.await();

      if (executionRegistry.isCancelled(requestId)) {
        sink.send("assistant-cancelled", cancelledEvent(preparedRequest));
        sink.complete();
        return;
      }

      Throwable streamException = streamFailure.get();
      if (streamException != null) {
        if (streamException instanceof LlmApiException llmApiException) {
          throw llmApiException;
        }
        if (streamException instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        if (streamException instanceof Exception exception) {
          throw exception;
        }
        throw new IllegalStateException("Unexpected throwable in llm provider stream", streamException);
      }

      // completed / cancelled / failed 只能有一个赢家。
      // 如果本地取消先抢到终态，这里的成功结果要被直接丢弃，不能覆盖 cancelled。
      if (!executionRegistry.tryMarkCompleted(requestId)) {
        if (executionRegistry.isCancelled(requestId)) {
          sink.send("assistant-cancelled", cancelledEvent(preparedRequest));
          sink.complete();
        }
        return;
      }

      // 到这里说明成功拿到了 completed 终态，可以安全回写最终结果。
      preparedRequest.requestEntity().setProviderRequestId(accumulator.providerRequestId);
      preparedRequest.requestEntity().setStatus(STATUS_COMPLETED);
      preparedRequest.requestEntity().setFinishedTime(Instant.now());
      documentLlmRequestRepository.update(preparedRequest.requestEntity());

      // assistant 消息在 terminal path 一次性落最终文本与元数据，避免 token 级频繁更新。
      preparedRequest.assistantMessage().setAssistantText(accumulator.assistantText.toString());
      preparedRequest.assistantMessage().setStatus(STATUS_COMPLETED);
      preparedRequest.assistantMessage().setFinishReason(accumulator.finishReason);
      preparedRequest.assistantMessage().setProviderUsageJson(writeJson(accumulator.usage));
      preparedRequest.assistantMessage().setProviderMetaJson(writeJson(filterProviderResponseMeta(accumulator.providerMeta)));
      preparedRequest.assistantMessage().setErrorCode(null);
      documentLlmMessageRepository.update(preparedRequest.assistantMessage());

      preparedRequest.session().setUpdatedTime(Instant.now());
      documentLlmSessionRepository.update(preparedRequest.session());

      sink.send("assistant-meta", metaEvent(preparedRequest, accumulator));
      sink.send("assistant-completed", completedEvent(preparedRequest, accumulator));
      sink.complete();
    } catch (LlmApiException exception) {
      handleProviderFailure(preparedRequest, sink, exception.errorCode(), exception);
    } catch (RuntimeException exception) {
      if (isProviderTimeoutException(exception)) {
        handleProviderFailure(
            preparedRequest,
            sink,
            LlmErrorCodes.LLM_PROVIDER_TIMEOUT,
            new LlmApiException(LlmErrorCodes.LLM_PROVIDER_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "模型上游服务响应超时。")
        );
        return;
      }
      log.error("Unexpected runtime error in llm provider stream, requestId={}", requestId, exception);
      handleProviderFailure(preparedRequest, sink, LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR, exception);
    } catch (Exception exception) {
      log.error("Unexpected error in llm provider stream, requestId={}", requestId, exception);
      handleProviderFailure(preparedRequest, sink, LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR, exception);
    } finally {
      executionRegistry.unregister(requestId);
    }
  }

  /**
   * 处理单个 provider chunk，并把增量信息并入累加器。
   */
  private void handleProviderChunk(
      PreparedRequest preparedRequest,
      StreamEventSink sink,
      StreamAccumulator accumulator,
      SpringAiProviderChunk chunk
  ) {
    // provider 的流式回包可能把 requestId、usage、finish_reason 分散在不同帧里，
    // 这里统一归并到 accumulator，最终由 terminal event 一次性吐给前端和数据库。
    if (chunk.providerRequestId() != null && !chunk.providerRequestId().isBlank()) {
      executionRegistry.attachProviderRequestId(preparedRequest.requestEntity().getRequestId(), chunk.providerRequestId());
      accumulator.providerRequestId = chunk.providerRequestId();
    }
    if (chunk.providerResponseMeta() != null) {
      accumulator.providerMeta.putAll(chunk.providerResponseMeta());
    }
    if (chunk.usage() != null) {
      accumulator.usage = chunk.usage();
    }
    if (chunk.finishReason() != null && !chunk.finishReason().isBlank()) {
      accumulator.finishReason = chunk.finishReason();
    }
    if (chunk.delta() != null && !chunk.delta().isEmpty()) {
      accumulator.assistantText.append(chunk.delta());
      // 用户取消后仍可能继续收到上游晚到 token，本地直接丢弃，不再往前端发 delta。
      if (!executionRegistry.isCancelled(preparedRequest.requestEntity().getRequestId())) {
        sink.send("assistant-delta", deltaEvent(preparedRequest, chunk.delta()));
      }
    }
  }

  /**
   * 统一处理 provider 执行失败。
   */
  private void handleProviderFailure(
      PreparedRequest preparedRequest,
      StreamEventSink sink,
      String errorCode,
      Exception exception
  ) {
    if (!executionRegistry.tryMarkFailed(preparedRequest.requestEntity().getRequestId())) {
      return;
    }
    markRequestFailed(preparedRequest.requestEntity(), preparedRequest.assistantMessage(), errorCode);
    sink.send("assistant-error", errorEvent(preparedRequest, errorCode));
    // 错误信息已经通过 SSE 显式返回给前端，这里只需要正常结束流，
    // 避免 response 已经切到 text/event-stream 后又被 Spring 当成 JSON 异常响应二次处理。
    sink.complete();
  }

  private void cancelPreparedRequest(PreparedRequest preparedRequest, String cancelSource) {
    DocumentLlmRequestEntity requestEntity = preparedRequest.requestEntity();
    if (!STATUS_IN_PROGRESS.equals(requestEntity.getStatus())) {
      return;
    }
    String requestId = requestEntity.getRequestId();
    boolean cancelClaimed = executionRegistry.tryMarkCancelled(requestId);
    if (!cancelClaimed && executionRegistry.hasExecution(requestId)) {
      return;
    }
    persistCancelledRequest(
        requestEntity,
        preparedRequest.assistantMessage(),
        preparedRequest.accessContext(),
        cancelSource
    );
  }

  private void persistCancelledRequest(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage,
      AccessContext accessContext,
      String cancelSource
  ) {
    documentLlmRequestRepository.markCancelRequested(
        requestEntity.getRequestId(),
        accessContext.tenantId(),
        accessContext.actorUser(),
        cancelSource
    );
    requestEntity.setCancelRequested(true);
    requestEntity.setCancelSource(cancelSource);
    requestEntity.setStatus(STATUS_CANCELLED);
    requestEntity.setFinishedTime(Instant.now());
    documentLlmRequestRepository.update(requestEntity);

    assistantMessage.setStatus(STATUS_CANCELLED);
    assistantMessage.setAssistantText(null);
    assistantMessage.setErrorCode(LlmErrorCodes.LLM_REQUEST_CANCELLED);
    assistantMessage.setFinishReason(null);
    documentLlmMessageRepository.update(assistantMessage);
  }

  /**
   * 把请求和 assistant 消息一起标记为失败。
   */
  private void markRequestFailed(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage,
      String errorCode
  ) {
    requestEntity.setStatus(STATUS_FAILED);
    requestEntity.setFinishedTime(Instant.now());
    documentLlmRequestRepository.update(requestEntity);
    assistantMessage.setStatus(STATUS_FAILED);
    assistantMessage.setErrorCode(errorCode);
    assistantMessage.setFinishReason(null);
    documentLlmMessageRepository.update(assistantMessage);
  }

  /**
   * 解析本次请求应使用的 provider、模型和底层实现。
   */
  private RuntimeSelection resolveSelection(SendLlmMessageRequest request) {
    // 这里要区分两个名字：
    // - providerName: 对前端暴露的逻辑 provider，例如 siliconflow
    // - springAiProvider: 真正执行请求的实现名，例如 openai-compatible / dashscope
    String providerName = request.provider() == null || request.provider().isBlank()
        ? llmProperties.resolveDefaultProvider()
        : request.provider().trim();
    LlmProperties.ProviderProperties providerProperties = llmProperties.getProvider(providerName);
    if (providerProperties == null || !providerProperties.isEnabled()) {
      throw new LlmApiException(LlmErrorCodes.LLM_PROVIDER_NOT_ALLOWED, HttpStatus.BAD_REQUEST, "未配置可用的 provider。");
    }
    String model = llmProperties.resolveModel(providerName, request.model());
    if (!llmProperties.isAllowedModel(providerName, model)) {
      throw new LlmApiException(LlmErrorCodes.LLM_MODEL_NOT_ALLOWED, HttpStatus.BAD_REQUEST, "当前 provider 不允许该模型。");
    }
    SpringAiLlmProvider provider = providerRegistry.findProvider(providerProperties.getSpringAiProvider())
        .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "未找到匹配的 Spring AI provider。"));
    return new RuntimeSelection(
        providerName,
        providerProperties.getBaseUrl(),
        providerProperties.getApiKey(),
        model,
        providerProperties.getTimeoutMillis(),
        provider
    );
  }

  /**
   * 在执行发送前校验 AI 功能和 provider 配置是否可用。
   */
  private void requireLlmEnabled() {
    if (!llmProperties.isFeatureEnabled() || !llmProperties.isEnabled()) {
      throw new LlmApiException(LlmErrorCodes.LLM_DISABLED, HttpStatus.SERVICE_UNAVAILABLE, "AI 工作台当前已禁用。");
    }
    if (!hasAnyConfiguredProvider()) {
      throw new LlmApiException(LlmErrorCodes.LLM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "当前未配置可用的模型 provider。");
    }
  }

  /**
   * 判断当前是否存在至少一个可用的逻辑 provider。
   */
  private boolean hasAnyConfiguredProvider() {
    return llmProperties.resolvedProviders().entrySet().stream()
        .anyMatch(entry -> llmProperties.hasUsableProvider(entry.getKey()) && findSpringAiProvider(entry.getKey()).isPresent());
  }

  /**
   * 构造前端能力页所需的 provider 选项列表。
   */
  private List<LlmProviderOptionResponse> buildAvailableProviderOptions() {
    List<LlmProviderOptionResponse> providers = new ArrayList<>();
    for (Map.Entry<String, LlmProperties.ProviderProperties> entry : llmProperties.resolvedProviders().entrySet()) {
      if (!entry.getValue().isEnabled()) {
        continue;
      }
      SpringAiLlmProvider provider = findSpringAiProvider(entry.getKey()).orElse(null);
      providers.add(new LlmProviderOptionResponse(
          entry.getKey(),
          entry.getValue().getLabel() == null || entry.getValue().getLabel().isBlank() ? entry.getKey() : entry.getValue().getLabel(),
          llmProperties.resolveModel(entry.getKey(), null),
          llmProperties.availableModels(entry.getKey()),
          provider != null && provider.supportsUpstreamCancel(),
          entry.getValue().isStreamingEnabled()
      ));
    }
    return List.copyOf(providers);
  }

  /**
   * 从逻辑 provider 名称解析到底层 Spring AI provider 实现。
   */
  private Optional<SpringAiLlmProvider> findSpringAiProvider(String logicalProviderName) {
    LlmProperties.ProviderProperties providerProperties = llmProperties.getProvider(logicalProviderName);
    if (providerProperties == null || providerProperties.getSpringAiProvider() == null || providerProperties.getSpringAiProvider().isBlank()) {
      return Optional.empty();
    }
    return providerRegistry.findProvider(providerProperties.getSpringAiProvider().trim());
  }

  /**
   * 构造请求刚开始时的初始 provider 元数据。
   */
  private Map<String, Object> initialProviderMeta(RuntimeSelection runtimeSelection) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("provider", runtimeSelection.providerName());
    meta.put("model", runtimeSelection.model());
    return meta;
  }

  /**
   * 构造 `request-started` 事件体。
   */
  private LlmStreamEventResponse startedEvent(PreparedRequest preparedRequest) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        null,
        null,
        null,
        initialProviderMeta(preparedRequest.runtimeSelection()),
        null,
        preparedRequest.requestEntity().getStartedTime(),
        null
    );
  }

  /**
   * 构造 `assistant-delta` 事件体。
   */
  private LlmStreamEventResponse deltaEvent(PreparedRequest preparedRequest, String delta) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        delta,
        null,
        null,
        null,
        Map.of(),
        null,
        preparedRequest.requestEntity().getStartedTime(),
        null
    );
  }

  /**
   * 构造 `assistant-meta` 事件体。
   */
  private LlmStreamEventResponse metaEvent(PreparedRequest preparedRequest, StreamAccumulator accumulator) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        null,
        new LlmUsageResponse(accumulator.usage.promptTokens(), accumulator.usage.completionTokens(), accumulator.usage.totalTokens()),
        accumulator.finishReason,
        filterProviderResponseMeta(accumulator.providerMeta),
        null,
        preparedRequest.requestEntity().getStartedTime(),
        null
    );
  }

  /**
   * 构造 `assistant-completed` 事件体。
   */
  private LlmStreamEventResponse completedEvent(PreparedRequest preparedRequest, StreamAccumulator accumulator) {
    Instant finishedTime = preparedRequest.requestEntity().getFinishedTime();
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        accumulator.assistantText.toString(),
        new LlmUsageResponse(accumulator.usage.promptTokens(), accumulator.usage.completionTokens(), accumulator.usage.totalTokens()),
        accumulator.finishReason,
        filterProviderResponseMeta(accumulator.providerMeta),
        null,
        preparedRequest.requestEntity().getStartedTime(),
        finishedTime
    );
  }

  /**
   * 构造 `assistant-cancelled` 事件体。
   */
  private LlmStreamEventResponse cancelledEvent(PreparedRequest preparedRequest) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        null,
        null,
        null,
        initialProviderMeta(preparedRequest.runtimeSelection()),
        LlmErrorCodes.LLM_REQUEST_CANCELLED,
        preparedRequest.requestEntity().getStartedTime(),
        Instant.now()
    );
  }

  /**
   * 构造 `assistant-error` 事件体。
   */
  private LlmStreamEventResponse errorEvent(PreparedRequest preparedRequest, String errorCode) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        null,
        null,
        null,
        initialProviderMeta(preparedRequest.runtimeSelection()),
        errorCode,
        preparedRequest.requestEntity().getStartedTime(),
        Instant.now()
    );
  }

  /**
   * 把数据库中的请求实体和 assistant 消息折叠成请求状态 DTO。
   */
  private LlmRequestStatusResponse toRequestStatusResponse(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage
  ) {
    return new LlmRequestStatusResponse(
        requestEntity.getDocumentId(),
        requestEntity.getRequestId(),
        requestEntity.getSessionId(),
        requestEntity.getAssistantMessageId(),
        requestEntity.getStatus(),
        assistantMessage.getAssistantText(),
        readUsage(assistantMessage.getProviderUsageJson()),
        assistantMessage.getFinishReason(),
        readMeta(assistantMessage.getProviderMetaJson()),
        Optional.ofNullable(assistantMessage.getErrorCode()).orElse(STATUS_CANCELLED.equals(requestEntity.getStatus()) ? LlmErrorCodes.LLM_REQUEST_CANCELLED : null),
        requestEntity.getStartedTime(),
        requestEntity.getFinishedTime()
    );
  }

  /**
   * 把会话实体和消息列表转换为详情响应。
   */
  private LlmSessionDetailResponse toSessionDetail(DocumentLlmSessionEntity entity, List<LlmMessageResponse> messages) {
    return new LlmSessionDetailResponse(
        entity.getSessionId(),
        entity.getDocumentId(),
        entity.getTitle(),
        entity.getLastSnapshotText(),
        entity.isLastSnapshotIsEmpty(),
        entity.getLastHeadingId(),
        entity.getLastHeadingText(),
        entity.getCreatedTime(),
        entity.getUpdatedTime(),
        messages
    );
  }

  /**
   * 把会话实体转换为摘要响应。
   */
  private LlmSessionSummaryResponse toSessionSummary(DocumentLlmSessionEntity entity) {
    return new LlmSessionSummaryResponse(
        entity.getSessionId(),
        entity.getDocumentId(),
        entity.getTitle(),
        entity.getLastSnapshotText(),
        entity.isLastSnapshotIsEmpty(),
        entity.getLastHeadingId(),
        entity.getLastHeadingText(),
        entity.getCreatedTime(),
        entity.getUpdatedTime()
    );
  }

  /**
   * 把消息实体转换为前端消息 DTO。
   */
  private LlmMessageResponse toMessageResponse(DocumentLlmMessageEntity entity) {
    return new LlmMessageResponse(
        entity.getMessageId(),
        entity.getRole(),
        entity.getMessageText(),
        entity.getAssistantText(),
        entity.getSnapshotText(),
        entity.isSnapshotIsEmpty(),
        entity.getHeadingId(),
        entity.getHeadingText(),
        entity.isIncludeHeading(),
        entity.getStatus(),
        entity.getErrorCode(),
        entity.getFinishReason(),
        readUsage(entity.getProviderUsageJson()),
        readMeta(entity.getProviderMetaJson()),
        entity.getCreatedTime()
    );
  }

  /**
   * 按白名单过滤 provider 响应元数据。
   *
   * <p>只允许前端和持久化真正需要的字段透出，避免把上游原始调试信息暴露出去。
   */
  private Map<String, Object> filterProviderResponseMeta(Map<String, Object> providerResponseMeta) {
    if (providerResponseMeta == null || providerResponseMeta.isEmpty()) {
      return Map.of();
    }
    // 只保留前端和持久化需要的白名单字段，避免把原始 provider 元数据、头信息或调试字段透出到浏览器。
    Map<String, Object> filtered = new LinkedHashMap<>();
    for (String key : llmProperties.getProviderResponseMetaAllowlist()) {
      if ("provider".equals(key) && providerResponseMeta.containsKey("provider")) {
        filtered.put("provider", providerResponseMeta.get("provider"));
      }
      if ("model".equals(key) && providerResponseMeta.containsKey("model")) {
        filtered.put("model", providerResponseMeta.get("model"));
      }
      if ("created".equals(key) && providerResponseMeta.containsKey("created")) {
        filtered.put("created", providerResponseMeta.get("created"));
      }
      if (key.startsWith("usage.") && providerResponseMeta.get("usage") instanceof Map<?, ?> usageMap) {
        filtered.putIfAbsent("usage", new LinkedHashMap<String, Object>());
        @SuppressWarnings("unchecked")
        Map<String, Object> usageTarget = (Map<String, Object>) filtered.get("usage");
        String usageKey = key.substring("usage.".length());
        usageTarget.put(usageKey, usageMap.get(usageKey));
      }
    }
    return filtered;
  }

  /**
   * 安全地把对象序列化成 JSON，失败时返回 `null` 并记录日志。
   */
  private String writeJson(Object payload) {
    if (payload == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      log.warn("Failed to serialize llm metadata, payloadType={}", payload.getClass().getSimpleName(), exception);
      return null;
    }
  }

  /**
   * 从 JSON 中读取 usage 信息。
   *
   * <p>优先按当前 record 结构解析，失败后回退到 map 兼容读取。
   */
  private LlmUsageResponse readUsage(String payload) {
    if (payload == null || payload.isBlank()) {
      return new LlmUsageResponse(null, null, null);
    }
    try {
      LlmProviderUsage usage = objectMapper.readValue(payload, LlmProviderUsage.class);
      return new LlmUsageResponse(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    } catch (Exception ignored) {
      try {
        Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<>() {
        });
        return new LlmUsageResponse(
            readInteger(map.get("promptTokens")),
            readInteger(map.get("completionTokens")),
            readInteger(map.get("totalTokens"))
        );
      } catch (Exception ignoredAgain) {
        return new LlmUsageResponse(null, null, null);
      }
    }
  }

  /**
   * 从 JSON 中读取 provider 元数据。
   */
  private Map<String, Object> readMeta(String payload) {
    if (payload == null || payload.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(payload, new TypeReference<>() {
      });
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  /**
   * 把任意数字对象安全转换为整数。
   */
  private Integer readInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private long resolveStreamTimeoutMillis(long providerTimeoutMillis) {
    if (providerTimeoutMillis <= 0) {
      return MIN_STREAM_TIMEOUT_MILLIS;
    }
    return Math.max(providerTimeoutMillis + STREAM_TIMEOUT_BUFFER_MILLIS, MIN_STREAM_TIMEOUT_MILLIS);
  }

  private boolean isProviderTimeoutException(RuntimeException exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof TimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * 选定后的 provider 运行参数。
   */
  private record RuntimeSelection(
      String providerName,
      String baseUrl,
      String apiKey,
      String model,
      long timeoutMillis,
      SpringAiLlmProvider provider
  ) {
  }

  /**
   * 一次发送请求在领域层中的完整准备结果。
   */
  private record PreparedRequest(
      DocumentLlmSessionEntity session,
      DocumentLlmMessageEntity userMessage,
      DocumentLlmMessageEntity assistantMessage,
      DocumentLlmRequestEntity requestEntity,
      SendLlmMessageRequest request,
      AccessContext accessContext,
      RuntimeSelection runtimeSelection,
      LlmRuntimeRequest runtimeRequest
  ) {
  }

  /**
   * provider 流式执行过程中的内存累加器。
   */
  private static final class StreamAccumulator {

    private final StringBuilder assistantText = new StringBuilder();
    private final Map<String, Object> providerMeta = new LinkedHashMap<>();
    private String providerRequestId;
    private LlmProviderUsage usage = new LlmProviderUsage(null, null, null);
    private String finishReason;

    /**
     * 用 provider 和 model 初始化元数据骨架。
     */
    private StreamAccumulator(String providerName, String model) {
      providerMeta.put("provider", providerName);
      providerMeta.put("model", model);
    }
  }

  /**
   * SSE 事件输出的最小抽象。
   */
  private interface StreamEventSink {

    /**
     * 发送一个命名事件。
     */
    void send(String name, LlmStreamEventResponse event);

    /**
     * 正常结束事件流。
     */
    void complete();

    /**
     * 以异常形式结束事件流。
     */
    void completeWithError(Exception exception);

    /**
     * 返回一个什么都不做的 sink，用于同步兼容接口复用同一执行链路。
     */
    static StreamEventSink noop() {
      return new StreamEventSink() {
        @Override
        public void send(String name, LlmStreamEventResponse event) {
        }

        @Override
        public void complete() {
        }

        @Override
        public void completeWithError(Exception exception) {
        }
      };
    }
  }

  /**
   * 基于 {@link SseEmitter} 的事件输出实现。
   */
  private static final class EmitterStreamEventSink implements StreamEventSink {

    private final SseEmitter emitter;
    private final Runnable onClosed;
    private final AtomicBoolean closeHandled = new AtomicBoolean(false);
    private volatile boolean closed;

    /**
     * 绑定 emitter 并在 completion / timeout / error 时关闭本地状态。
     */
    private EmitterStreamEventSink(SseEmitter emitter, Runnable onClosed) {
      this.emitter = emitter;
      this.onClosed = onClosed == null ? () -> {
      } : onClosed;
      this.emitter.onCompletion(this::markClosed);
      this.emitter.onTimeout(this::markClosed);
      this.emitter.onError(error -> markClosed());
    }

    /**
     * 发送单个 SSE 事件。
     */
    @Override
    public void send(String name, LlmStreamEventResponse event) {
      if (closed) {
        return;
      }
      try {
        emitter.send(SseEmitter.event().name(name).data(event));
      } catch (IOException | IllegalStateException exception) {
        markClosed();
      }
    }

    /**
     * 正常关闭 emitter。
     */
    @Override
    public void complete() {
      if (closed) {
        return;
      }
      markClosed();
      emitter.complete();
    }

    /**
     * 以异常形式关闭 emitter。
     */
    @Override
    public void completeWithError(Exception exception) {
      if (closed) {
        return;
      }
      markClosed();
      emitter.completeWithError(exception);
    }

    private void markClosed() {
      closed = true;
      if (closeHandled.compareAndSet(false, true)) {
        onClosed.run();
      }
    }
  }
}
