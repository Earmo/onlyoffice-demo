package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.CurrentAccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageVariantEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmRequestEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmSessionEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmMessageRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmMessageVariantRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmRequestRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmSessionRepository;
import com.earmo.onlyoffice.integration.exception.LlmApiException;
import com.earmo.onlyoffice.integration.model.llm.CreateLlmSessionRequest;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmMessageResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmMessageVariantResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmProviderOptionResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDetailResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionSummaryResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmStreamEventResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmUsageResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.earmo.onlyoffice.integration.service.llm.provider.SpringAiProviderRegistry;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
  private static final int AUTO_SESSION_TITLE_MAX_LENGTH = 24;
  // 流式链路需要给上游 provider timeout 留收口余量，避免“上游已超时但 servlet 先把请求砍掉”。
  private static final long STREAM_TIMEOUT_BUFFER_MILLIS = 30000L;
  private static final long MIN_STREAM_TIMEOUT_MILLIS = 300000L;
  private static final DateTimeFormatter SESSION_TITLE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
      .withZone(ZoneId.of("Asia/Shanghai"));

  private final LlmProperties llmProperties;
  private final SpringAiProviderRegistry providerRegistry;
  private final DocumentLlmSessionRepository documentLlmSessionRepository;
  private final DocumentLlmMessageRepository documentLlmMessageRepository;
  private final DocumentLlmMessageVariantRepository documentLlmMessageVariantRepository;
  private final DocumentLlmRequestRepository documentLlmRequestRepository;
  private final LlmConversationAccessGuard accessGuard;
  private final LlmRequestExecutionRegistry executionRegistry;
  private final LlmPromptWindowBuilder promptWindowBuilder;
  private final ObjectMapper objectMapper;
  private final Executor llmExecutor;
  private final Map<String, Instant> explicitActiveSwitchTimes = new ConcurrentHashMap<>();

  /**
   * 注入会话流程所需的仓储、provider 适配器和执行器。
   *
   * @param llmProperties LLM 功能配置。
   * @param providerRegistry Spring AI provider 注册表。
   * @param documentLlmSessionRepository AI 会话仓储。
   * @param documentLlmMessageRepository AI 消息仓储。
   * @param documentLlmMessageVariantRepository assistant 多版本回复仓储。
   * @param documentLlmRequestRepository AI 请求状态仓储。
   * @param accessGuard AI 会话访问权限守卫。
   * @param executionRegistry 请求执行态注册表。
   * @param promptWindowBuilder prompt 历史窗口构造器。
   * @param objectMapper JSON 序列化组件。
   * @param llmExecutor LLM 异步执行线程池。
   */
  public LlmConversationService(
      LlmProperties llmProperties,
      SpringAiProviderRegistry providerRegistry,
      DocumentLlmSessionRepository documentLlmSessionRepository,
      DocumentLlmMessageRepository documentLlmMessageRepository,
      DocumentLlmMessageVariantRepository documentLlmMessageVariantRepository,
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
    this.documentLlmMessageVariantRepository = documentLlmMessageVariantRepository;
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
   *
   * @param documentId 内部文档 ID。
   * @return 当前文档的 AI 能力、默认模型和可选 provider 列表。
   */
  public LlmCapabilityResponse getCapability(String documentId) {
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
   *
   * @param documentId 内部文档 ID。
   * @return 当前用户可见的 AI 会话摘要列表。
   */
  public List<LlmSessionSummaryResponse> listSessions(String documentId) {
    return listSessions(documentId, CurrentAccessContext.getRequired());
  }

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
   *
   * @param request 创建会话请求。
   * @return 新建会话详情。
   */
  public LlmSessionDetailResponse createSession(CreateLlmSessionRequest request) {
    return createSession(request, CurrentAccessContext.getRequired());
  }

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
    entity.setLastConversationTime(now);
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
   * 删除会话（软删除）。
   *
   * @param documentId 内部文档 ID。
   * @param sessionId AI 会话 ID。
   */
  public void deleteSession(String documentId, String sessionId) {
    deleteSession(documentId, sessionId, CurrentAccessContext.getRequired());
  }

  public void deleteSession(String documentId, String sessionId, AccessContext accessContext) {
    DocumentLlmSessionEntity session = accessGuard.requireSession(documentId, sessionId, accessContext);
    session.setArchivedTime(Instant.now());
    session.setUpdatedTime(Instant.now());
    documentLlmSessionRepository.update(session);
  }

  /**
   * 重命名 AI 会话。
   *
   * @param documentId 内部文档 ID。
   * @param sessionId AI 会话 ID。
   * @param newTitle 新会话标题。
   */
  public void renameSession(String documentId, String sessionId, String newTitle) {
    renameSession(documentId, sessionId, newTitle, CurrentAccessContext.getRequired());
  }

  public void renameSession(String documentId, String sessionId, String newTitle, AccessContext accessContext) {
    DocumentLlmSessionEntity session = accessGuard.requireSession(documentId, sessionId, accessContext);
    session.setTitle(newTitle);
    session.setUpdatedTime(Instant.now());
    documentLlmSessionRepository.update(session);
  }

  /**
   * 获取单个会话详情及消息列表。
   *
   * @param documentId 内部文档 ID。
   * @param sessionId AI 会话 ID。
   * @return 会话详情与消息列表。
   */
  public LlmSessionDetailResponse getSession(String documentId, String sessionId) {
    return getSession(documentId, sessionId, CurrentAccessContext.getRequired());
  }

  public LlmSessionDetailResponse getSession(String documentId, String sessionId, AccessContext accessContext) {
    DocumentLlmSessionEntity session = accessGuard.requireSession(documentId, sessionId, accessContext);
    List<DocumentLlmMessageEntity> messageEntities = documentLlmMessageRepository.findMessagesBySessionScope(
        sessionId,
        documentId,
        accessContext.tenantId(),
        accessContext.actorUser(),
        llmProperties.getSession().getMaxMessagesPerSession()
    );
    Map<String, List<DocumentLlmMessageVariantEntity>> variantsByMessageId = variantsByMessageId(
        messageEntities,
        documentId,
        accessContext
    );
    List<LlmMessageResponse> messages = messageEntities.stream()
        .map(message -> toMessageResponse(message, variantsByMessageId.getOrDefault(message.getMessageId(), List.of())))
        .toList();
    return toSessionDetail(session, messages);
  }

  /**
   * 显式切换 assistant message 的 active variant。
   *
   * @param documentId 内部文档 ID。
   * @param messageId assistant message ID。
   * @param request active variant 切换请求。
   * @return 切换后的 assistant message 响应。
   */
  public LlmMessageResponse setActiveVariant(String documentId, String messageId, com.earmo.onlyoffice.integration.model.llm.SetLlmActiveVariantRequest request) {
    return setActiveVariant(documentId, messageId, request, CurrentAccessContext.getRequired());
  }

  public LlmMessageResponse setActiveVariant(String documentId, String messageId, com.earmo.onlyoffice.integration.model.llm.SetLlmActiveVariantRequest request, AccessContext accessContext) {
    DocumentLlmMessageEntity assistantMessage = accessGuard.requireAssistantMessage(documentId, request.sessionId(), messageId, accessContext);
    List<DocumentLlmMessageVariantEntity> variants = documentLlmMessageVariantRepository.findByMessageScope(
        messageId,
        documentId,
        accessContext.tenantId(),
        accessContext.actorUser()
    );
    DocumentLlmMessageVariantEntity selectedVariant = variants.stream()
        .filter(variant -> request.variantId() != null && request.variantId().equals(variant.getVariantId())
            || request.variantIndex() != null && request.variantIndex().equals(variant.getVariantIndex()))
        .findFirst()
        .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "assistant variant 不存在。"));
    assistantMessage.setActiveVariantIndex(selectedVariant.getVariantIndex());
    copyVariantToAssistantMessage(assistantMessage, selectedVariant);
    documentLlmMessageRepository.update(assistantMessage);
    explicitActiveSwitchTimes.put(messageId, Instant.now());
    log.info(
        "已切换 LLM active variant，documentId={}, sessionId={}, assistantMessageId={}, variantIndex={}, activeVariantIndex={}",
        documentId,
        request.sessionId(),
        messageId,
        selectedVariant.getVariantIndex(),
        assistantMessage.getActiveVariantIndex()
    );
    return toMessageResponse(assistantMessage, variants);
  }

  /**
   * 加载指定会话的消息并合并 assistant variants。
   *
   * @param sessionId AI 会话 ID。
   * @param documentId 内部文档 ID。
   * @param accessContext 当前访问上下文。
   * @return 已合并 variants 的消息响应列表。
   */
  private List<LlmMessageResponse> loadSessionMessages(String sessionId, String documentId, AccessContext accessContext) {
    List<DocumentLlmMessageEntity> messageEntities = documentLlmMessageRepository.findMessagesBySessionScope(
            sessionId,
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser(),
            llmProperties.getSession().getMaxMessagesPerSession()
        );
    Map<String, List<DocumentLlmMessageVariantEntity>> variantsByMessageId = variantsByMessageId(messageEntities, documentId, accessContext);
    return messageEntities.stream()
        .map(message -> toMessageResponse(message, variantsByMessageId.getOrDefault(message.getMessageId(), List.of())))
        .toList();
  }

  /**
   * 兼容旧同步接口发送消息。
   *
   * <p>内部仍然使用流式执行链路，只是在当前线程短暂等待一段时间，
   * 若窗口内未完成则直接返回 `in_progress`。
   *
   * @param request 发送消息请求。
   * @return 本次请求的同步快照状态。
   */
  public LlmRequestStatusResponse sendMessage(SendLlmMessageRequest request) {
    return sendMessage(request, CurrentAccessContext.getRequired());
  }

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
   *
   * @param request 发送消息请求。
   * @return 已打开的 SSE emitter。
   */
  public SseEmitter streamMessage(SendLlmMessageRequest request) {
    return streamMessage(request, CurrentAccessContext.getRequired());
  }

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
        "已打开 LLM 流，requestId={}, provider={}, model={}, providerTimeoutMs={}, streamTimeoutMs={}",
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
   *
   * @param documentId 内部文档 ID。
   * @param requestId AI 请求 ID。
   * @return 请求当前状态快照。
   */
  public LlmRequestStatusResponse getRequest(String documentId, String requestId) {
    return getRequest(documentId, requestId, CurrentAccessContext.getRequired());
  }

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
   *
   * @param documentId 内部文档 ID。
   * @param requestId AI 请求 ID。
   * @return 取消后的请求状态快照。
   */
  public LlmRequestStatusResponse cancelRequest(String documentId, String requestId) {
    return cancelRequest(documentId, requestId, CurrentAccessContext.getRequired());
  }

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
   *
   * @param request 发送消息请求。
   * @param accessContext 当前访问上下文。
   * @return 已落库并准备执行的请求上下文。
   */
  private PreparedRequest beginRequest(SendLlmMessageRequest request, AccessContext accessContext) {
    requireLlmEnabled();
    RuntimeSelection runtimeSelection = resolveSelection(request);
    DocumentLlmSessionEntity session = accessGuard.requireSession(request.documentId(), request.sessionId(), accessContext);
    Instant now = Instant.now();
    boolean regenerate = request.regenerateAssistantMessageId() != null && !request.regenerateAssistantMessageId().isBlank();
    boolean firstConversationTurn = documentLlmMessageRepository.findMessagesBySessionScope(
        session.getSessionId(),
        session.getDocumentId(),
        accessContext.tenantId(),
        accessContext.actorUser(),
        1
    ).isEmpty();

    DocumentLlmMessageEntity assistantMessage;
    DocumentLlmMessageEntity userMessage;
    Integer previousActiveVariantIndex;
    if (regenerate) {
      assistantMessage = accessGuard.requireAssistantMessage(
          request.documentId(),
          session.getSessionId(),
          request.regenerateAssistantMessageId(),
          accessContext
      );
      previousActiveVariantIndex = assistantMessage.getActiveVariantIndex();
      List<DocumentLlmMessageEntity> messages = documentLlmMessageRepository.findMessagesBySessionScope(
          session.getSessionId(),
          session.getDocumentId(),
          accessContext.tenantId(),
          accessContext.actorUser(),
          llmProperties.getSession().getMaxMessagesPerSession()
      );
      userMessage = findUserMessageForAssistant(messages, assistantMessage)
          .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.BAD_REQUEST, "regenerate 原始 user 消息不存在。"));
      log.info(
          "开始 LLM regenerate 建单，documentId={}, sessionId={}, assistantMessageId={}, previousActiveVariantIndex={}",
          request.documentId(),
          session.getSessionId(),
          assistantMessage.getMessageId(),
          previousActiveVariantIndex
      );
    } else {
      previousActiveVariantIndex = null;
      // 固定的首次建单顺序：user message、assistant 容器、variant 0、request。
      userMessage = new DocumentLlmMessageEntity();
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

      assistantMessage = new DocumentLlmMessageEntity();
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
      assistantMessage.setActiveVariantIndex(0);
      assistantMessage.setProviderMetaJson(writeJson(initialProviderMeta(runtimeSelection)));
      assistantMessage.setCreatedTime(now);
      documentLlmMessageRepository.insert(assistantMessage);
    }

    DocumentLlmMessageVariantEntity variant = documentLlmMessageVariantRepository.createNextVariantForMessageScope(
        assistantMessage.getMessageId(),
        request.documentId(),
        accessContext.tenantId(),
        accessContext.actorUser(),
        STATUS_PENDING,
        now
    );
    variant.setProviderMetaJson(writeJson(initialProviderMeta(runtimeSelection)));
    documentLlmMessageVariantRepository.update(variant);
    log.info(
        "已创建 LLM message variant，documentId={}, sessionId={}, assistantMessageId={}, variantId={}, variantIndex={}",
        request.documentId(),
        session.getSessionId(),
        assistantMessage.getMessageId(),
        variant.getVariantId(),
        variant.getVariantIndex()
    );

    // request 记录只承载执行态，不直接保存文本内容。
    DocumentLlmRequestEntity requestEntity = new DocumentLlmRequestEntity();
    requestEntity.setRequestId(UUID.randomUUID().toString());
    requestEntity.setSessionId(session.getSessionId());
    requestEntity.setDocumentId(request.documentId());
    requestEntity.setTenantId(accessContext.tenantId());
    requestEntity.setActorUser(accessContext.actorUser());
    requestEntity.setUserMessageId(userMessage.getMessageId());
    requestEntity.setAssistantMessageId(assistantMessage.getMessageId());
    requestEntity.setVariantId(variant.getVariantId());
    requestEntity.setVariantIndex(variant.getVariantIndex());
    requestEntity.setStatus(STATUS_IN_PROGRESS);
    requestEntity.setCancelRequested(false);
    requestEntity.setStartedTime(now);
    documentLlmRequestRepository.insert(requestEntity);

    // 会话表只保留“最近一次发送”的摘要，便于列表页和详情页快速展示。
    if (firstConversationTurn && shouldAutoRenameSession(session)) {
      String generatedTitle = generateSessionTitle(request.question());
      session.setTitle(generatedTitle);
      log.info(
          "首次对话后自动命名 LLM 会话：documentId={}, sessionId={}, title={}",
          request.documentId(),
          session.getSessionId(),
          generatedTitle
      );
    }
    session.setLastSnapshotText(request.selectionSnapshot().text());
    session.setLastSnapshotIsEmpty(request.selectionSnapshot().emptySelection());
    session.setLastHeadingId(request.headingContext().headingId());
    session.setLastHeadingText(request.headingContext().headingText());
    session.setLastConversationTime(now);
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
    Map<String, String> activeAssistantTextByMessageId = activeVariantTextsByMessageId(history, request.documentId(), accessContext);

    LlmRuntimeRequest runtimeRequest = new LlmRuntimeRequest(
        runtimeSelection.providerName(),
        runtimeSelection.baseUrl(),
        runtimeSelection.apiKey(),
        runtimeSelection.model(),
        runtimeSelection.timeoutMillis(),
        promptWindowBuilder.buildMessages(
            llmProperties,
            history,
            activeAssistantTextByMessageId,
            request.question(),
            request.selectionSnapshot().text(),
            request.selectionSnapshot().emptySelection(),
            llmProperties.isAllowHeadingContext() && request.headingContext().includeHeading(),
            request.headingContext().headingText()
        )
    );

    log.info(
        "已准备 LLM 请求，documentId={}, sessionId={}, provider={}, springAiProvider={}, model={}, timeoutMs={}, messageCount={}, selectionEmpty={}, includeHeading={}",
        request.documentId(),
        session.getSessionId(),
        runtimeSelection.providerName(),
        runtimeSelection.provider().providerName(),
        runtimeSelection.model(),
        runtimeSelection.timeoutMillis(),
        runtimeRequest.messages().size(),
        request.selectionSnapshot().emptySelection(),
        llmProperties.isAllowHeadingContext() && request.headingContext().includeHeading()
    );

    return new PreparedRequest(
        session,
        userMessage,
        assistantMessage,
        requestEntity,
        variant,
        previousActiveVariantIndex,
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
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param sink SSE 事件输出端。
   */
  private void executeProviderStream(PreparedRequest preparedRequest, StreamEventSink sink) {
    String requestId = preparedRequest.requestEntity().getRequestId();
    StreamAccumulator accumulator = new StreamAccumulator(preparedRequest.runtimeSelection().providerName(), preparedRequest.runtimeSelection().model());
    try {
      if (executionRegistry.isCancelled(requestId)) {
        log.info(
            "请求已取消，跳过 LLM 执行，requestId={}, documentId={}, sessionId={}",
            requestId,
            preparedRequest.request().documentId(),
            preparedRequest.request().sessionId()
        );
        persistCancelledRequest(
            preparedRequest.requestEntity(),
            preparedRequest.assistantMessage(),
            preparedRequest.accessContext(),
            CANCEL_SOURCE_USER,
            accumulator
        );
        sink.send("assistant-cancelled", cancelledEvent(preparedRequest, accumulator));
        sink.complete();
        return;
      }
      log.info(
          "开始执行 LLM provider 流式请求，requestId={}, documentId={}, sessionId={}, provider={}, model={}, timeoutMs={}",
          requestId,
          preparedRequest.request().documentId(),
          preparedRequest.request().sessionId(),
          preparedRequest.runtimeSelection().providerName(),
          preparedRequest.runtimeSelection().model(),
          preparedRequest.runtimeSelection().timeoutMillis()
      );
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
        log.info(
            "LLM 流结束后请求已被取消，requestId={}, provider={}, model={}, chunkCount={}",
            requestId,
            preparedRequest.runtimeSelection().providerName(),
            preparedRequest.runtimeSelection().model(),
            accumulator.chunkCount
        );
        persistCancelledRequest(
            preparedRequest.requestEntity(),
            preparedRequest.assistantMessage(),
            preparedRequest.accessContext(),
            CANCEL_SOURCE_CLIENT_DISCONNECT,
            accumulator
        );
        sink.send("assistant-cancelled", cancelledEvent(preparedRequest, accumulator));
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
          log.info(
              "取消请求先完成，丢弃本次 LLM 完成结果，requestId={}, provider={}, model={}, chunkCount={}",
              requestId,
              preparedRequest.runtimeSelection().providerName(),
              preparedRequest.runtimeSelection().model(),
              accumulator.chunkCount
          );
          persistCancelledRequest(
              preparedRequest.requestEntity(),
              preparedRequest.assistantMessage(),
              preparedRequest.accessContext(),
              CANCEL_SOURCE_USER,
              accumulator
          );
          sink.send("assistant-cancelled", cancelledEvent(preparedRequest, accumulator));
          sink.complete();
        }
        return;
      }

      // 到这里说明成功拿到了 completed 终态，可以安全回写最终结果。
      preparedRequest.requestEntity().setProviderRequestId(accumulator.providerRequestId);
      preparedRequest.requestEntity().setStatus(STATUS_COMPLETED);
      preparedRequest.requestEntity().setFinishedTime(Instant.now());
      documentLlmRequestRepository.update(preparedRequest.requestEntity());

      applyFinalVariantContent(preparedRequest.variant(), accumulator);
      documentLlmMessageVariantRepository.update(preparedRequest.variant());
      if (shouldAutoActivateCompletedVariant(preparedRequest)) {
        preparedRequest.assistantMessage().setActiveVariantIndex(preparedRequest.variant().getVariantIndex());
      } else {
        log.info(
            "LLM completed terminal 未覆盖用户 active variant 选择，requestId={}, assistantMessageId={}, variantIndex={}, activeVariantIndex={}",
            requestId,
            preparedRequest.assistantMessage().getMessageId(),
            preparedRequest.variant().getVariantIndex(),
            preparedRequest.assistantMessage().getActiveVariantIndex()
        );
      }
      copyVariantToAssistantMessage(preparedRequest.assistantMessage(), activeVariantForMessage(preparedRequest.assistantMessage(), preparedRequest.variant()));
      documentLlmMessageRepository.update(preparedRequest.assistantMessage());

      preparedRequest.session().setUpdatedTime(Instant.now());
      documentLlmSessionRepository.update(preparedRequest.session());

      log.info(
          "LLM 请求已完成，requestId={}, documentId={}, sessionId={}, assistantMessageId={}, variantIndex={}, activeVariantIndex={}, provider={}, model={}, upstreamRequestId={}, chunkCount={}, responseChars={}, finishReason={}, usage={}",
          requestId,
          preparedRequest.request().documentId(),
          preparedRequest.request().sessionId(),
          preparedRequest.assistantMessage().getMessageId(),
          preparedRequest.variant().getVariantIndex(),
          preparedRequest.assistantMessage().getActiveVariantIndex(),
          preparedRequest.runtimeSelection().providerName(),
          preparedRequest.runtimeSelection().model(),
          accumulator.providerRequestId,
          accumulator.chunkCount,
          accumulator.assistantText.length(),
          accumulator.finishReason,
          accumulator.usage
      );

      sink.send("assistant-meta", metaEvent(preparedRequest, accumulator));
      sink.send("assistant-completed", completedEvent(preparedRequest, accumulator));
      sink.complete();
    } catch (LlmApiException exception) {
      handleProviderFailure(preparedRequest, sink, accumulator, exception.errorCode(), exception);
    } catch (RuntimeException exception) {
      if (isProviderTimeoutException(exception)) {
        handleProviderFailure(
            preparedRequest,
            sink,
            accumulator,
            LlmErrorCodes.LLM_PROVIDER_TIMEOUT,
            new LlmApiException(LlmErrorCodes.LLM_PROVIDER_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "模型上游服务响应超时。")
        );
        return;
      }
      log.error("LLM provider 流执行时出现未预期的运行时异常，requestId={}", requestId, exception);
      handleProviderFailure(preparedRequest, sink, accumulator, LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR, exception);
    } catch (Exception exception) {
      log.error("LLM provider 流执行时出现未预期异常，requestId={}", requestId, exception);
      handleProviderFailure(preparedRequest, sink, accumulator, LlmErrorCodes.LLM_PROVIDER_UPSTREAM_ERROR, exception);
    } finally {
      executionRegistry.unregister(requestId);
    }
  }

  /**
   * 处理单个 provider chunk，并把增量信息并入累加器。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param sink SSE 事件输出端。
   * @param accumulator 当前流式累计器。
   * @param chunk provider 返回的单个流式片段。
   */
  private void handleProviderChunk(
      PreparedRequest preparedRequest,
      StreamEventSink sink,
      StreamAccumulator accumulator,
      SpringAiProviderChunk chunk
  ) {
    accumulator.chunkCount++;
    if (executionRegistry.isCancelled(preparedRequest.requestEntity().getRequestId())) {
      log.info(
          "LLM 请求已取消，丢弃上游晚到 chunk，requestId={}, provider={}, model={}, chunkCount={}",
          preparedRequest.requestEntity().getRequestId(),
          preparedRequest.runtimeSelection().providerName(),
          preparedRequest.runtimeSelection().model(),
          accumulator.chunkCount
      );
      return;
    }
    // provider 的流式回包可能把 requestId、usage、finish_reason 分散在不同帧里，
    // 这里统一归并到 accumulator，最终由 terminal event 一次性吐给前端和数据库。
    if (chunk.providerRequestId() != null && !chunk.providerRequestId().isBlank()) {
      executionRegistry.attachProviderRequestId(preparedRequest.requestEntity().getRequestId(), chunk.providerRequestId());
      accumulator.providerRequestId = chunk.providerRequestId();
    }
    if (chunk.providerResponseMeta() != null) {
      for (Map.Entry<String, Object> entry : chunk.providerResponseMeta().entrySet()) {
        if ("reasoningContent".equals(entry.getKey()) && entry.getValue() instanceof String newDelta) {
          appendReasoningContent(accumulator, newDelta);
          if (!executionRegistry.isCancelled(preparedRequest.requestEntity().getRequestId())) {
            if (!accumulator.firstReasoningDeltaLogged) {
              accumulator.firstReasoningDeltaLogged = true;
              log.info(
                  "收到首个 LLM 推理增量片段，requestId={}, provider={}, model={}, upstreamRequestId={}, chunkCount={}, reasoningChars={}",
                  preparedRequest.requestEntity().getRequestId(),
                  preparedRequest.runtimeSelection().providerName(),
                  preparedRequest.runtimeSelection().model(),
                  accumulator.providerRequestId,
                  accumulator.chunkCount,
                  newDelta.length()
              );
            }
            sink.send("reasoning-delta", reasoningDeltaEvent(preparedRequest, newDelta));
          }
        } else {
          accumulator.providerMeta.put(entry.getKey(), entry.getValue());
        }
      }
    }
    if (chunk.usage() != null) {
      accumulator.usage = chunk.usage();
    }
    if (chunk.finishReason() != null && !chunk.finishReason().isBlank()) {
      accumulator.finishReason = chunk.finishReason();
    }
    if (chunk.delta() != null && !chunk.delta().isEmpty()) {
      accumulator.assistantText.append(chunk.delta());
      if (!accumulator.firstDeltaLogged) {
        accumulator.firstDeltaLogged = true;
        log.info("收到首个 LLM 增量片段，requestId={}, provider={}, model={}, upstreamRequestId={}, chunkCount={}, deltaChars={}",
            preparedRequest.requestEntity().getRequestId(),preparedRequest.runtimeSelection().providerName(),
            preparedRequest.runtimeSelection().model(),accumulator.providerRequestId,accumulator.chunkCount,chunk.delta().length()
        );
      }
      // 用户取消后仍可能继续收到上游晚到 token，本地直接丢弃，不再往前端发 delta。
      if (!executionRegistry.isCancelled(preparedRequest.requestEntity().getRequestId())) {
        sink.send("assistant-delta", deltaEvent(preparedRequest, chunk.delta()));
      }
    }
  }

  /**
   * 追加 provider 返回的推理文本增量。
   *
   * @param accumulator 当前流式累计器。
   * @param newDelta 新收到的推理文本增量。
   */
  private void appendReasoningContent(StreamAccumulator accumulator, String newDelta) {
    if (newDelta == null || newDelta.isEmpty()) {
      return;
    }
    // 推理内容是流式增量，需要拼接而非覆盖（与 assistantText 的 append 逻辑一致）。
    // 如果某个 provider 返回累积式 reasoning，应在 provider adapter 层先规范化成增量。
    String existing = (String) accumulator.providerMeta.getOrDefault("reasoningContent", "");
    accumulator.providerMeta.put("reasoningContent", existing + newDelta);
  }

  /**
   * 统一处理 provider 执行失败。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param sink SSE 事件输出端。
   * @param accumulator 当前流式累计器。
   * @param errorCode 发送给前端并持久化的稳定错误码。
   * @param exception provider 执行异常。
   */
  private void handleProviderFailure(
      PreparedRequest preparedRequest,
      StreamEventSink sink,
      StreamAccumulator accumulator,
      String errorCode,
      Exception exception
  ) {
    if (!executionRegistry.tryMarkFailed(preparedRequest.requestEntity().getRequestId())) {
      return;
    }
    log.info(
        "LLM 请求失败，requestId={}, documentId={}, sessionId={}, provider={}, model={}, errorCode={}, message={}",
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().documentId(),
        preparedRequest.request().sessionId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        errorCode,
        exception.getMessage()
    );
    markRequestFailed(preparedRequest.requestEntity(), preparedRequest.assistantMessage(), preparedRequest.variant(), errorCode, accumulator);
    sink.send("assistant-error", errorEvent(preparedRequest, errorCode, accumulator));
    // 错误信息已经通过 SSE 显式返回给前端，这里只需要正常结束流，
    // 避免 response 已经切到 text/event-stream 后又被 Spring 当成 JSON 异常响应二次处理。
    sink.complete();
  }

  /**
   * 取消已准备但仍在执行中的请求。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param cancelSource 取消来源。
   */
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
    log.info(
        "开始取消 LLM 请求，requestId={}, documentId={}, sessionId={}, provider={}, model={}, cancelSource={}",
        requestId,
        requestEntity.getDocumentId(),
        requestEntity.getSessionId(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        cancelSource
    );
    persistCancelledRequest(
        requestEntity,
        preparedRequest.assistantMessage(),
        preparedRequest.accessContext(),
        cancelSource
    );
  }

  /**
   * 持久化取消状态。
   *
   * @param requestEntity AI 请求实体。
   * @param assistantMessage assistant 消息实体。
   * @param accessContext 当前访问上下文。
   * @param cancelSource 取消来源。
   */
  private void persistCancelledRequest(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage,
      AccessContext accessContext,
      String cancelSource
  ) {
    persistCancelledRequest(requestEntity, assistantMessage, accessContext, cancelSource, null);
  }

  /**
   * 持久化取消状态，并可保留已经产生的部分输出。
   *
   * @param requestEntity AI 请求实体。
   * @param assistantMessage assistant 消息实体。
   * @param accessContext 当前访问上下文。
   * @param cancelSource 取消来源。
   * @param accumulator 当前流式累计器，可为空。
   */
  private void persistCancelledRequest(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage,
      AccessContext accessContext,
      String cancelSource,
      StreamAccumulator accumulator
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

    DocumentLlmMessageVariantEntity variant = documentLlmMessageVariantRepository.findByMessageScope(
            assistantMessage.getMessageId(),
            requestEntity.getVariantId(),
            requestEntity.getDocumentId(),
            accessContext.tenantId(),
            accessContext.actorUser()
        )
        .orElse(null);
    boolean variantUpdated = false;
    if (variant != null && (accumulator != null || !executionRegistry.hasExecution(requestEntity.getRequestId()))) {
      variant.setStatus(STATUS_CANCELLED);
      applyPartialVariantContent(variant, accumulator);
      variant.setErrorCode(LlmErrorCodes.LLM_REQUEST_CANCELLED);
      variant.setUpdatedTime(Instant.now());
      documentLlmMessageVariantRepository.update(variant);
      variantUpdated = true;
    }
    if (variantUpdated) {
      DocumentLlmMessageVariantEntity activeVariant = activeVariantForMessage(assistantMessage, variant);
      copyVariantToAssistantMessage(assistantMessage, activeVariant);
      documentLlmMessageRepository.update(assistantMessage);
    }
  }

  /**
   * 把请求和 assistant 消息一起标记为失败。
   *
   * @param requestEntity AI 请求实体。
   * @param assistantMessage assistant 消息实体。
   * @param variant 本次请求生成或更新的 variant。
   * @param errorCode 失败错误码。
   * @param accumulator 当前流式累计器。
   */
  private void markRequestFailed(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage,
      DocumentLlmMessageVariantEntity variant,
      String errorCode,
      StreamAccumulator accumulator
  ) {
    requestEntity.setStatus(STATUS_FAILED);
    requestEntity.setFinishedTime(Instant.now());
    documentLlmRequestRepository.update(requestEntity);
    variant.setStatus(STATUS_FAILED);
    applyPartialVariantContent(variant, accumulator);
    variant.setErrorCode(errorCode);
    variant.setUpdatedTime(Instant.now());
    documentLlmMessageVariantRepository.update(variant);
    DocumentLlmMessageVariantEntity activeVariant = activeVariantForMessage(assistantMessage, variant);
    copyVariantToAssistantMessage(assistantMessage, activeVariant);
    documentLlmMessageRepository.update(assistantMessage);
  }

  /**
   * 将取消或失败前已经收到的部分内容写入 variant。
   *
   * @param variant 本次请求生成或更新的 variant。
   * @param accumulator 当前流式累计器，可为空。
   */
  private void applyPartialVariantContent(
      DocumentLlmMessageVariantEntity variant,
      StreamAccumulator accumulator
  ) {
    if (accumulator == null || !accumulator.hasPartialContent()) {
      return;
    }
    variant.setAssistantText(accumulator.assistantText.toString());
    if (accumulator.finishReason != null && !accumulator.finishReason.isBlank()) {
      variant.setFinishReason(accumulator.finishReason);
    }
    if (hasUsage(accumulator.usage)) {
      variant.setProviderUsageJson(writeJson(accumulator.usage));
    }
    Map<String, Object> filteredMeta = filterProviderResponseMeta(accumulator.providerMeta);
    if (!filteredMeta.isEmpty()) {
      variant.setProviderMetaJson(writeJson(filteredMeta));
    }
  }

  /**
   * 将成功终态的完整输出写入 variant。
   *
   * @param variant 本次请求生成或更新的 variant。
   * @param accumulator 当前流式累计器。
   */
  private void applyFinalVariantContent(DocumentLlmMessageVariantEntity variant, StreamAccumulator accumulator) {
    variant.setAssistantText(accumulator.assistantText.toString());
    variant.setStatus(STATUS_COMPLETED);
    variant.setFinishReason(accumulator.finishReason);
    variant.setProviderUsageJson(writeJson(accumulator.usage));
    variant.setProviderMetaJson(writeJson(filterProviderResponseMeta(accumulator.providerMeta)));
    variant.setErrorCode(null);
    variant.setUpdatedTime(Instant.now());
  }

  /**
   * 判断本次完成的 variant 是否应自动成为 active variant。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @return true 表示用户没有在请求期间显式切换版本，可以自动激活本次完成版本。
   */
  private boolean shouldAutoActivateCompletedVariant(PreparedRequest preparedRequest) {
    DocumentLlmMessageEntity currentAssistantMessage = documentLlmMessageRepository.findMessageByScope(
            preparedRequest.assistantMessage().getMessageId(),
            preparedRequest.assistantMessage().getDocumentId(),
            preparedRequest.assistantMessage().getTenantId(),
            preparedRequest.assistantMessage().getActorUser()
        )
        .orElse(preparedRequest.assistantMessage());
    preparedRequest.assistantMessage().setActiveVariantIndex(currentAssistantMessage.getActiveVariantIndex());
    Instant explicitSwitchTime = explicitActiveSwitchTimes.get(preparedRequest.assistantMessage().getMessageId());
    if (explicitSwitchTime != null && explicitSwitchTime.isAfter(preparedRequest.requestEntity().getStartedTime())) {
      return false;
    }
    Integer currentActive = preparedRequest.assistantMessage().getActiveVariantIndex();
    Integer previousActive = preparedRequest.previousActiveVariantIndex();
    return currentActive == null && previousActive == null || currentActive != null && currentActive.equals(previousActive);
  }

  /**
   * 获取 assistant message 当前 active variant。
   *
   * @param assistantMessage assistant 消息实体。
   * @param fallbackVariant 找不到 active variant 时使用的兜底版本。
   * @return 当前 active variant 或兜底版本。
   */
  private DocumentLlmMessageVariantEntity activeVariantForMessage(
      DocumentLlmMessageEntity assistantMessage,
      DocumentLlmMessageVariantEntity fallbackVariant
  ) {
    Integer activeVariantIndex = assistantMessage.getActiveVariantIndex();
    if (activeVariantIndex == null) {
      return fallbackVariant;
    }
    return documentLlmMessageVariantRepository.findByMessageScope(
            assistantMessage.getMessageId(),
            assistantMessage.getDocumentId(),
            assistantMessage.getTenantId(),
            assistantMessage.getActorUser()
        )
        .stream()
        .filter(variant -> activeVariantIndex.equals(variant.getVariantIndex()))
        .findFirst()
        .orElse(fallbackVariant);
  }

  /**
   * 将 active variant 的内容同步回 assistant message 容器字段。
   *
   * @param assistantMessage assistant 消息实体。
   * @param variant 当前 active variant，可为空。
   */
  private void copyVariantToAssistantMessage(
      DocumentLlmMessageEntity assistantMessage,
      DocumentLlmMessageVariantEntity variant
  ) {
    if (variant == null) {
      return;
    }
    assistantMessage.setAssistantText(variant.getAssistantText());
    assistantMessage.setStatus(variant.getStatus());
    assistantMessage.setFinishReason(variant.getFinishReason());
    assistantMessage.setProviderUsageJson(variant.getProviderUsageJson());
    assistantMessage.setProviderMetaJson(variant.getProviderMetaJson());
    assistantMessage.setErrorCode(variant.getErrorCode());
  }

  /**
   * 在会话消息列表中查找指定 assistant message 对应的上一条 user message。
   *
   * @param messages 按时间排序的会话消息列表。
   * @param assistantMessage 目标 assistant 消息。
   * @return 对应的 user 消息；不存在时为空。
   */
  private Optional<DocumentLlmMessageEntity> findUserMessageForAssistant(
      List<DocumentLlmMessageEntity> messages,
      DocumentLlmMessageEntity assistantMessage
  ) {
    DocumentLlmMessageEntity latestUser = null;
    for (DocumentLlmMessageEntity message : messages) {
      if ("user".equals(message.getRole())) {
        latestUser = message;
      }
      if (message.getMessageId().equals(assistantMessage.getMessageId())) {
        return Optional.ofNullable(latestUser);
      }
    }
    return Optional.empty();
  }

  /**
   * 将历史消息中的 assistant 内容替换为各自 active variant 文本。
   *
   * @param history 会话历史消息列表。
   * @param documentId 内部文档 ID。
   * @param accessContext 当前访问上下文。
   */
  private void applyActiveVariantsToHistory(
      List<DocumentLlmMessageEntity> history,
      String documentId,
      AccessContext accessContext
  ) {
    Map<String, List<DocumentLlmMessageVariantEntity>> variantsByMessageId = variantsByMessageId(history, documentId, accessContext);
    for (DocumentLlmMessageEntity message : history) {
      if (!"assistant".equals(message.getRole())) {
        continue;
      }
      DocumentLlmMessageVariantEntity activeVariant = selectActiveVariant(
          message,
          variantsByMessageId.getOrDefault(message.getMessageId(), List.of())
      ).orElse(null);
      copyVariantToAssistantMessage(message, activeVariant);
    }
  }

  /**
   * 构造 assistant message ID 到 active variant 文本的映射。
   *
   * @param history 会话历史消息列表。
   * @param documentId 内部文档 ID。
   * @param accessContext 当前访问上下文。
   * @return assistant message ID 到 active variant 文本的映射。
   */
  private Map<String, String> activeVariantTextsByMessageId(
      List<DocumentLlmMessageEntity> history,
      String documentId,
      AccessContext accessContext
  ) {
    Map<String, List<DocumentLlmMessageVariantEntity>> variantsByMessageId = variantsByMessageId(history, documentId, accessContext);
    Map<String, String> activeTexts = new LinkedHashMap<>();
    for (DocumentLlmMessageEntity message : history) {
      if (!"assistant".equals(message.getRole())) {
        continue;
      }
      selectActiveVariant(message, variantsByMessageId.getOrDefault(message.getMessageId(), List.of()))
          .map(DocumentLlmMessageVariantEntity::getAssistantText)
          .ifPresent(text -> activeTexts.put(message.getMessageId(), text));
    }
    return activeTexts;
  }

  /**
   * 批量加载 assistant messages 下的 variants 并按 messageId 分组。
   *
   * @param messages 会话历史消息列表。
   * @param documentId 内部文档 ID。
   * @param accessContext 当前访问上下文。
   * @return messageId 到 variants 列表的映射。
   */
  private Map<String, List<DocumentLlmMessageVariantEntity>> variantsByMessageId(
      List<DocumentLlmMessageEntity> messages,
      String documentId,
      AccessContext accessContext
  ) {
    List<String> assistantMessageIds = messages.stream()
        .filter(message -> "assistant".equals(message.getRole()))
        .map(DocumentLlmMessageEntity::getMessageId)
        .toList();
    return documentLlmMessageVariantRepository.findByMessageIdsScope(
            assistantMessageIds,
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser()
        )
        .stream()
        .collect(Collectors.groupingBy(DocumentLlmMessageVariantEntity::getMessageId, LinkedHashMap::new, Collectors.toList()));
  }

  /**
   * 从 variants 中选择当前应展示的 active variant。
   *
   * @param message assistant 消息实体。
   * @param variants 该 assistant 消息下的所有版本。
   * @return active variant；没有可选版本时为空。
   */
  private Optional<DocumentLlmMessageVariantEntity> selectActiveVariant(
      DocumentLlmMessageEntity message,
      List<DocumentLlmMessageVariantEntity> variants
  ) {
    if (variants == null || variants.isEmpty()) {
      return Optional.empty();
    }
    Integer activeVariantIndex = message.getActiveVariantIndex();
    if (activeVariantIndex != null) {
      Optional<DocumentLlmMessageVariantEntity> active = variants.stream()
          .filter(variant -> activeVariantIndex.equals(variant.getVariantIndex()))
          .findFirst();
      if (active.isPresent()) {
        return active;
      }
    }
    return variants.stream().filter(variant -> STATUS_COMPLETED.equals(variant.getStatus())).findFirst()
        .or(() -> variants.stream().findFirst());
  }

  /**
   * 判断 provider usage 是否包含任意 token 统计字段。
   *
   * @param usage provider usage 对象。
   * @return true 表示至少一个 token 字段不为空。
   */
  private boolean hasUsage(LlmProviderUsage usage) {
    return usage != null && (usage.promptTokens() != null || usage.completionTokens() != null || usage.totalTokens() != null);
  }

  /**
   * 解析本次请求应使用的 provider、模型和底层实现。
   *
   * @param request 发送消息请求。
   * @return 已解析的运行时 provider、模型和超时配置。
   */
  private RuntimeSelection resolveSelection(SendLlmMessageRequest request) {
    // 这里要区分两个名字：
    // - providerName: 对前端暴露的逻辑 provider，例如 siliconflow
    // - springAiProvider: 真正执行请求的实现名，例如 openai-compatible
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
    log.info(
        "已解析 LLM 运行时选择，requestedProvider={}, resolvedProvider={}, springAiProvider={}, requestedModel={}, resolvedModel={}, timeoutMs={}",
        request.provider(),
        providerName,
        provider.providerName(),
        request.model(),
        model,
        providerProperties.getTimeoutMillis()
    );
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
   *
   * @throws LlmApiException AI 功能关闭或没有可用 provider 时抛出。
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
   *
   * @return true 表示至少有一个 provider 配置完整且存在底层实现。
   */
  private boolean hasAnyConfiguredProvider() {
    return llmProperties.resolvedProviders().entrySet().stream()
        .anyMatch(entry -> llmProperties.hasUsableProvider(entry.getKey()) && findSpringAiProvider(entry.getKey()).isPresent());
  }

  /**
   * 构造前端能力页所需的 provider 选项列表。
   *
   * @return 前端可展示的 provider 选项列表。
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
   *
   * @param logicalProviderName 逻辑 provider 名称。
   * @return 匹配的底层 Spring AI provider；未配置或未注册时为空。
   */
  private Optional<SpringAiLlmProvider> findSpringAiProvider(String logicalProviderName) {
    LlmProperties.ProviderProperties providerProperties = llmProperties.getProvider(logicalProviderName);
    if (providerProperties == null || providerProperties.getSpringAiProvider() == null || providerProperties.getSpringAiProvider().isBlank()) {
      return Optional.empty();
    }
    return providerRegistry.findProvider(providerProperties.getSpringAiProvider().trim());
  }

  /**
   * 默认会话在首轮对话后自动改名；显式命名或历史会话不被覆盖。
   *
   * @param session 待判断的 AI 会话实体。
   * @return true 表示本次首轮对话完成后可以自动生成标题。
   */
  private boolean shouldAutoRenameSession(DocumentLlmSessionEntity session) {
    String title = Optional.ofNullable(session.getTitle()).orElse("").trim();
    return title.isEmpty() || title.startsWith("新会话 ");
  }

  /**
   * 从首条问题里提取短标题，避免把完整 prompt 直接塞进会话列表。
   *
   * @param question 用户首条问题正文。
   * @return 适合展示在会话列表中的短标题。
   */
  private String generateSessionTitle(String question) {
    String title = Optional.ofNullable(question).orElse("")
        .replaceAll("\\s+", " ")
        .trim();
    for (String prefix : List.of("请帮我把", "请帮我", "帮我把", "帮我", "请问", "请", "能否")) {
      if (title.startsWith(prefix)) {
        title = title.substring(prefix.length()).trim();
        break;
      }
    }
    title = title.replaceAll("[\\p{Punct}，。！？；：、]+$", "").trim();
    if (title.isEmpty()) {
      return "新对话";
    }
    if (title.codePointCount(0, title.length()) <= AUTO_SESSION_TITLE_MAX_LENGTH) {
      return title;
    }
    int endIndex = title.offsetByCodePoints(0, AUTO_SESSION_TITLE_MAX_LENGTH);
    return title.substring(0, endIndex).trim() + "...";
  }

  /**
   * 构造请求刚开始时的初始 provider 元数据。
   *
   * @param runtimeSelection 本次请求的运行时选择。
   * @return 包含 provider 和 model 的初始元数据。
   */
  private Map<String, Object> initialProviderMeta(RuntimeSelection runtimeSelection) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("provider", runtimeSelection.providerName());
    meta.put("model", runtimeSelection.model());
    return meta;
  }

  /**
   * 构造 `request-started` 事件体。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @return request-started SSE payload。
   */
  private LlmStreamEventResponse startedEvent(PreparedRequest preparedRequest) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
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
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param delta 本次 assistant 正文增量。
   * @return assistant-delta SSE payload。
   */
  private LlmStreamEventResponse deltaEvent(PreparedRequest preparedRequest, String delta) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        delta,
        null,
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
   * 构造 `reasoning-delta` 事件体。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param reasoningText 本次推理文本增量。
   * @return reasoning-delta SSE payload。
   */
  private LlmStreamEventResponse reasoningDeltaEvent(PreparedRequest preparedRequest, String reasoningText) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        reasoningText,
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
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param accumulator 当前流式累计器。
   * @return assistant-meta SSE payload。
   */
  private LlmStreamEventResponse metaEvent(PreparedRequest preparedRequest, StreamAccumulator accumulator) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
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
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param accumulator 当前流式累计器。
   * @return assistant-completed SSE payload。
   */
  private LlmStreamEventResponse completedEvent(PreparedRequest preparedRequest, StreamAccumulator accumulator) {
    Instant finishedTime = preparedRequest.requestEntity().getFinishedTime();
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
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
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @return assistant-cancelled SSE payload。
   */
  private LlmStreamEventResponse cancelledEvent(PreparedRequest preparedRequest) {
    return cancelledEvent(preparedRequest, null);
  }

  /**
   * 构造带部分输出内容的 `assistant-cancelled` 事件体。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param accumulator 当前流式累计器，可为空。
   * @return assistant-cancelled SSE payload。
   */
  private LlmStreamEventResponse cancelledEvent(PreparedRequest preparedRequest, StreamAccumulator accumulator) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        null,
        accumulator == null ? null : accumulator.assistantText.toString(),
        null,
        null,
        accumulator == null ? initialProviderMeta(preparedRequest.runtimeSelection()) : filterProviderResponseMeta(accumulator.providerMeta),
        LlmErrorCodes.LLM_REQUEST_CANCELLED,
        preparedRequest.requestEntity().getStartedTime(),
        Instant.now()
    );
  }

  /**
   * 构造 `assistant-error` 事件体。
   *
   * @param preparedRequest 已准备好的请求上下文。
   * @param errorCode 发送给前端的稳定错误码。
   * @param accumulator 当前流式累计器，可为空。
   * @return assistant-error SSE payload。
   */
  private LlmStreamEventResponse errorEvent(PreparedRequest preparedRequest, String errorCode, StreamAccumulator accumulator) {
    return new LlmStreamEventResponse(
        preparedRequest.request().documentId(),
        preparedRequest.requestEntity().getRequestId(),
        preparedRequest.request().sessionId(),
        preparedRequest.session().getTitle(),
        preparedRequest.assistantMessage().getMessageId(),
        preparedRequest.variant().getVariantId(),
        preparedRequest.variant().getVariantIndex(),
        preparedRequest.assistantMessage().getActiveVariantIndex(),
        preparedRequest.runtimeSelection().providerName(),
        preparedRequest.runtimeSelection().model(),
        null,
        null,
        accumulator == null ? null : accumulator.assistantText.toString(),
        null,
        null,
        accumulator == null ? initialProviderMeta(preparedRequest.runtimeSelection()) : filterProviderResponseMeta(accumulator.providerMeta),
        errorCode,
        preparedRequest.requestEntity().getStartedTime(),
        Instant.now()
    );
  }

  /**
   * 把数据库中的请求实体和 assistant 消息折叠成请求状态 DTO。
   *
   * @param requestEntity AI 请求实体。
   * @param assistantMessage 本次请求关联的 assistant 消息实体。
   * @return 请求状态响应 DTO。
   */
  private LlmRequestStatusResponse toRequestStatusResponse(
      DocumentLlmRequestEntity requestEntity,
      DocumentLlmMessageEntity assistantMessage
  ) {
    DocumentLlmMessageVariantEntity variant = requestEntity.getVariantId() == null ? null : documentLlmMessageVariantRepository.findByMessageScope(
            assistantMessage.getMessageId(),
            requestEntity.getVariantId(),
            requestEntity.getDocumentId(),
            requestEntity.getTenantId(),
            requestEntity.getActorUser()
        )
        .orElse(null);
    return new LlmRequestStatusResponse(
        requestEntity.getDocumentId(),
        requestEntity.getRequestId(),
        requestEntity.getSessionId(),
        requestEntity.getAssistantMessageId(),
        requestEntity.getVariantId(),
        requestEntity.getVariantIndex(),
        assistantMessage.getActiveVariantIndex(),
        requestEntity.getStatus(),
        variant == null ? assistantMessage.getAssistantText() : variant.getAssistantText(),
        readUsage(variant == null ? assistantMessage.getProviderUsageJson() : variant.getProviderUsageJson()),
        variant == null ? assistantMessage.getFinishReason() : variant.getFinishReason(),
        readMeta(variant == null ? assistantMessage.getProviderMetaJson() : variant.getProviderMetaJson()),
        Optional.ofNullable(variant == null ? assistantMessage.getErrorCode() : variant.getErrorCode()).orElse(STATUS_CANCELLED.equals(requestEntity.getStatus()) ? LlmErrorCodes.LLM_REQUEST_CANCELLED : null),
        requestEntity.getStartedTime(),
        requestEntity.getFinishedTime()
    );
  }

  /**
   * 把会话实体和消息列表转换为详情响应。
   *
   * @param entity AI 会话实体。
   * @param messages 会话消息 DTO 列表。
   * @return 会话详情响应。
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
        entity.getLastConversationTime(),
        entity.getCreatedTime(),
        entity.getUpdatedTime(),
        messages
    );
  }

  /**
   * 把会话实体转换为摘要响应。
   *
   * @param entity AI 会话实体。
   * @return 会话摘要响应。
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
        entity.getLastConversationTime(),
        entity.getCreatedTime(),
        entity.getUpdatedTime()
    );
  }

  /**
   * 把消息实体转换为前端消息 DTO。
   *
   * @param entity AI 消息实体。
   * @return 消息响应 DTO。
   */
  private LlmMessageResponse toMessageResponse(DocumentLlmMessageEntity entity) {
    return toMessageResponse(entity, List.of());
  }

  /**
   * 把消息实体和 assistant variants 合并成前端消息 DTO。
   *
   * @param entity AI 消息实体。
   * @param variants assistant 消息下的所有回复版本。
   * @return 消息响应 DTO。
   */
  private LlmMessageResponse toMessageResponse(DocumentLlmMessageEntity entity, List<DocumentLlmMessageVariantEntity> variants) {
    Optional<DocumentLlmMessageVariantEntity> activeVariant = selectActiveVariant(entity, variants);
    List<LlmMessageVariantResponse> variantResponses = variants == null ? List.of() : variants.stream()
        .map(this::toVariantResponse)
        .toList();
    return new LlmMessageResponse(
        entity.getMessageId(),
        entity.getRole(),
        entity.getMessageText(),
        activeVariant.map(DocumentLlmMessageVariantEntity::getAssistantText).orElse(entity.getAssistantText()),
        entity.getSnapshotText(),
        entity.isSnapshotIsEmpty(),
        entity.getHeadingId(),
        entity.getHeadingText(),
        entity.isIncludeHeading(),
        activeVariant.map(DocumentLlmMessageVariantEntity::getStatus).orElse(entity.getStatus()),
        activeVariant.map(DocumentLlmMessageVariantEntity::getErrorCode).orElse(entity.getErrorCode()),
        activeVariant.map(DocumentLlmMessageVariantEntity::getFinishReason).orElse(entity.getFinishReason()),
        readUsage(activeVariant.map(DocumentLlmMessageVariantEntity::getProviderUsageJson).orElse(entity.getProviderUsageJson())),
        readMeta(activeVariant.map(DocumentLlmMessageVariantEntity::getProviderMetaJson).orElse(entity.getProviderMetaJson())),
        variantResponses,
        entity.getActiveVariantIndex(),
        entity.getCreatedTime()
    );
  }

  /**
   * 把 assistant variant 实体转换为响应 DTO。
   *
   * @param variant assistant 回复版本实体。
   * @return assistant 回复版本响应 DTO。
   */
  private LlmMessageVariantResponse toVariantResponse(DocumentLlmMessageVariantEntity variant) {
    return new LlmMessageVariantResponse(
        variant.getVariantId(),
        variant.getVariantIndex() == null ? 0 : variant.getVariantIndex(),
        variant.getAssistantText(),
        variant.getStatus(),
        variant.getErrorCode(),
        variant.getFinishReason(),
        readUsage(variant.getProviderUsageJson()),
        readMeta(variant.getProviderMetaJson()),
        variant.getCreatedTime()
    );
  }

  /**
   * 按白名单过滤 provider 响应元数据。
   *
   * <p>只允许前端和持久化真正需要的字段透出，避免把上游原始调试信息暴露出去。
   *
   * @param providerResponseMeta provider 原始响应元数据。
   * @return 白名单过滤后的元数据。
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
      if ("reasoningContent".equals(key) && providerResponseMeta.containsKey("reasoningContent")) {
        filtered.put("reasoningContent", providerResponseMeta.get("reasoningContent"));
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
   *
   * @param payload 待序列化对象。
   * @return JSON 字符串；payload 为空或序列化失败时返回 null。
   */
  private String writeJson(Object payload) {
    if (payload == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      log.warn("序列化 LLM 元数据失败，payloadType={}", payload.getClass().getSimpleName(), exception);
      return null;
    }
  }

  /**
   * 从 JSON 中读取 usage 信息。
   *
   * <p>优先按当前 record 结构解析，失败后回退到 map 兼容读取。
   *
   * @param payload usage JSON 字符串。
   * @return usage 响应对象；解析失败时返回空 usage。
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
   *
   * @param payload provider 元数据 JSON 字符串。
   * @return 元数据映射；解析失败时返回空映射。
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
   *
   * @param value 待转换对象。
   * @return 整数值；非数字对象返回 null。
   */
  private Integer readInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  /**
   * 解析 SSE emitter 的超时时间。
   *
   * @param providerTimeoutMillis provider 调用超时时间。
   * @return 加上缓冲后的 SSE 超时时间。
   */
  private long resolveStreamTimeoutMillis(long providerTimeoutMillis) {
    if (providerTimeoutMillis <= 0) {
      return MIN_STREAM_TIMEOUT_MILLIS;
    }
    return Math.max(providerTimeoutMillis + STREAM_TIMEOUT_BUFFER_MILLIS, MIN_STREAM_TIMEOUT_MILLIS);
  }

  /**
   * 判断异常链中是否包含 provider 超时异常。
   *
   * @param exception provider 执行阶段抛出的运行时异常。
   * @return true 表示异常链中存在 TimeoutException。
   */
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
      DocumentLlmMessageVariantEntity variant,
      Integer previousActiveVariantIndex,
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
    private int chunkCount;
    private boolean firstDeltaLogged;
    private boolean firstReasoningDeltaLogged;

    /**
     * 用 provider 和 model 初始化元数据骨架。
     *
     * @param providerName 逻辑 provider 名称。
     * @param model 模型名称。
     */
    private StreamAccumulator(String providerName, String model) {
      providerMeta.put("provider", providerName);
      providerMeta.put("model", model);
    }

    /**
     * 判断当前累计器是否已经收到可保留的部分内容。
     *
     * @return true 表示已有正文或推理内容。
     */
    private boolean hasPartialContent() {
      return !assistantText.isEmpty() || providerMeta.containsKey("reasoningContent");
    }
  }

  /**
   * SSE 事件输出的最小抽象。
   */
  private interface StreamEventSink {

    /**
     * 发送一个命名事件。
     *
     * @param name SSE 事件名称。
     * @param event SSE 事件 payload。
     */
    void send(String name, LlmStreamEventResponse event);

    /**
     * 正常结束事件流。
     */
    void complete();

    /**
     * 以异常形式结束事件流。
     *
     * @param exception 触发流结束的异常。
     */
    void completeWithError(Exception exception);

    /**
     * 返回一个什么都不做的 sink，用于同步兼容接口复用同一执行链路。
     *
     * @return 空实现的事件输出端。
     */
    static StreamEventSink noop() {
      return new StreamEventSink() {
        /**
         * 丢弃命名 SSE 事件。
         *
         * @param name SSE 事件名称。
         * @param event SSE 事件 payload。
         */
        @Override
        public void send(String name, LlmStreamEventResponse event) {
        }

        /**
         * 空实现的正常结束动作。
         */
        @Override
        public void complete() {
        }

        /**
         * 空实现的异常结束动作。
         *
         * @param exception 触发流结束的异常。
         */
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
    private final AtomicBoolean completionSignalled = new AtomicBoolean(false);
    private volatile boolean closed;

    /**
     * 绑定 emitter 并在 completion / timeout / error 时关闭本地状态。
     *
     * @param emitter Spring MVC SSE emitter。
     * @param onClosed 连接关闭时执行的回调。
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
     *
     * @param name SSE 事件名称。
     * @param event SSE 事件 payload。
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
      if (!completionSignalled.compareAndSet(false, true)) {
        return;
      }
      markClosed();
      try {
        emitter.complete();
      } catch (IllegalStateException ignored) {
        // 连接已关闭时仍然允许这里静默收口，避免 servlet async 上下文悬挂到超时。
      }
    }

    /**
     * 以异常形式关闭 emitter。
     *
     * @param exception 触发流关闭的异常。
     */
    @Override
    public void completeWithError(Exception exception) {
      if (!completionSignalled.compareAndSet(false, true)) {
        return;
      }
      markClosed();
      try {
        emitter.completeWithError(exception);
      } catch (IllegalStateException ignored) {
        // 与 complete() 保持相同语义：连接已不可用时只做本地收口，不再放大异常。
      }
    }

    /**
     * 标记连接已经关闭并执行一次性关闭回调。
     */
    private void markClosed() {
      closed = true;
      if (closeHandled.compareAndSet(false, true)) {
        onClosed.run();
      }
    }
  }
}
