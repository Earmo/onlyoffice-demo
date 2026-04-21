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
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDetailResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionSummaryResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmUsageResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LlmConversationService {

  private static final String STATUS_PENDING = "pending";
  private static final String STATUS_IN_PROGRESS = "in_progress";
  private static final String STATUS_COMPLETED = "completed";
  private static final String STATUS_FAILED = "failed";
  private static final String STATUS_CANCELLED = "cancelled";
  private static final DateTimeFormatter SESSION_TITLE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
      .withZone(ZoneId.of("Asia/Shanghai"));

  private final LlmProperties llmProperties;
  private final List<LlmProviderStrategy> providerStrategies;
  private final DocumentLlmSessionRepository documentLlmSessionRepository;
  private final DocumentLlmMessageRepository documentLlmMessageRepository;
  private final DocumentLlmRequestRepository documentLlmRequestRepository;
  private final LlmConversationAccessGuard accessGuard;
  private final LlmRequestExecutionRegistry executionRegistry;
  private final LlmPromptWindowBuilder promptWindowBuilder;
  private final ObjectMapper objectMapper;
  private final Executor llmExecutor = Executors.newCachedThreadPool();

  public LlmConversationService(
      LlmProperties llmProperties,
      List<LlmProviderStrategy> providerStrategies,
      DocumentLlmSessionRepository documentLlmSessionRepository,
      DocumentLlmMessageRepository documentLlmMessageRepository,
      DocumentLlmRequestRepository documentLlmRequestRepository,
      LlmConversationAccessGuard accessGuard,
      LlmRequestExecutionRegistry executionRegistry,
      LlmPromptWindowBuilder promptWindowBuilder,
      ObjectMapper objectMapper
  ) {
    this.llmProperties = llmProperties;
    this.providerStrategies = providerStrategies;
    this.documentLlmSessionRepository = documentLlmSessionRepository;
    this.documentLlmMessageRepository = documentLlmMessageRepository;
    this.documentLlmRequestRepository = documentLlmRequestRepository;
    this.accessGuard = accessGuard;
    this.executionRegistry = executionRegistry;
    this.promptWindowBuilder = promptWindowBuilder;
    this.objectMapper = objectMapper;
  }

  public LlmCapabilityResponse getCapability(String documentId, AccessContext accessContext) {
    LlmProviderStrategy strategy = resolveProvider();
    if (!llmProperties.isFeatureEnabled() || !llmProperties.isEnabled()) {
      return new LlmCapabilityResponse(documentId, false, LlmErrorCodes.LLM_DISABLED, llmProperties.getProvider(), llmProperties.getModel(), false);
    }
    if (!llmProperties.isConfigured() || strategy == null) {
      return new LlmCapabilityResponse(documentId, false, LlmErrorCodes.LLM_UNAVAILABLE, llmProperties.getProvider(), llmProperties.getModel(), false);
    }
    return new LlmCapabilityResponse(documentId, true, null, llmProperties.getProvider(), llmProperties.getModel(), strategy.supportsUpstreamCancel());
  }

  public List<LlmSessionSummaryResponse> listSessions(String documentId, AccessContext accessContext) {
    return documentLlmSessionRepository.findSessionsByScope(documentId, accessContext.tenantId(), accessContext.actorUser(), 50)
        .stream()
        .map(this::toSessionSummary)
        .toList();
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

  public LlmRequestStatusResponse sendMessage(SendLlmMessageRequest request, AccessContext accessContext) {
    requireLlmEnabled();
    DocumentLlmSessionEntity session = accessGuard.requireSession(request.documentId(), request.sessionId(), accessContext);
    Instant now = Instant.now();

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
    assistantMessage.setCreatedTime(now);
    documentLlmMessageRepository.insert(assistantMessage);

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

    session.setLastSnapshotText(request.selectionSnapshot().text());
    session.setLastSnapshotIsEmpty(request.selectionSnapshot().emptySelection());
    session.setLastHeadingId(request.headingContext().headingId());
    session.setLastHeadingText(request.headingContext().headingText());
    session.setUpdatedTime(now);
    documentLlmSessionRepository.update(session);

    LlmProviderStrategy providerStrategy = requireProvider();
    executionRegistry.register(requestEntity.getRequestId(), providerStrategy);
    CompletableFuture<Void> execution = CompletableFuture.runAsync(
        () -> executeProviderCall(session, userMessage, assistantMessage, requestEntity, request, accessContext, providerStrategy),
        llmExecutor
    );

    try {
      execution.get(llmProperties.getRequestSyncWaitMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ignored) {
      // 超过同步窗口就交给轮询接口继续观察。
    } catch (Exception ignored) {
      // executeProviderCall 已经把失败态写回数据库，这里只回当前 request 视图。
    }
    return getRequest(request.documentId(), requestEntity.getRequestId(), accessContext);
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

  public LlmRequestStatusResponse cancelRequest(String documentId, String requestId, AccessContext accessContext) {
    DocumentLlmRequestEntity requestEntity = accessGuard.requireRequest(documentId, requestId, accessContext);
    documentLlmRequestRepository.markCancelRequested(requestId, accessContext.tenantId(), accessContext.actorUser(), "user");
    executionRegistry.cancel(requestId);

    requestEntity.setCancelRequested(true);
    requestEntity.setCancelSource("user");
    requestEntity.setStatus(STATUS_CANCELLED);
    requestEntity.setFinishedTime(Instant.now());
    documentLlmRequestRepository.update(requestEntity);

    DocumentLlmMessageEntity assistantMessage = documentLlmMessageRepository.findMessageByScope(
            requestEntity.getAssistantMessageId(),
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser()
        )
        .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "assistant 消息不存在。"));
    assistantMessage.setStatus(STATUS_CANCELLED);
    assistantMessage.setAssistantText(null);
    assistantMessage.setErrorCode(LlmErrorCodes.LLM_REQUEST_CANCELLED);
    documentLlmMessageRepository.update(assistantMessage);
    return toRequestStatusResponse(requestEntity, assistantMessage);
  }

  private void executeProviderCall(
      DocumentLlmSessionEntity session,
      DocumentLlmMessageEntity userMessage,
      DocumentLlmMessageEntity assistantMessage,
      DocumentLlmRequestEntity requestEntity,
      SendLlmMessageRequest request,
      AccessContext accessContext,
      LlmProviderStrategy providerStrategy
  ) {
    try {
      List<DocumentLlmMessageEntity> history = new ArrayList<>(documentLlmMessageRepository.findMessagesBySessionScope(
          session.getSessionId(),
          session.getDocumentId(),
          accessContext.tenantId(),
          accessContext.actorUser(),
          llmProperties.getSession().getMaxMessagesPerSession()
      ));
      history.removeIf(message -> message.getMessageId().equals(assistantMessage.getMessageId()) || STATUS_PENDING.equals(message.getStatus()));

      LlmProviderResponse providerResponse = providerStrategy.sendChat(new LlmProviderRequest(
          llmProperties.getModel(),
          promptWindowBuilder.buildMessages(
              llmProperties,
              history,
              request.question(),
              request.selectionSnapshot().text(),
              request.selectionSnapshot().emptySelection(),
              llmProperties.isAllowHeadingContext() && request.headingContext().includeHeading(),
              request.headingContext().headingText()
          )
      ));
      executionRegistry.attachProviderRequestId(requestEntity.getRequestId(), providerResponse.providerRequestId());

      if (executionRegistry.isCancelled(requestEntity.getRequestId())) {
        return;
      }

      requestEntity.setProviderRequestId(providerResponse.providerRequestId());
      requestEntity.setStatus(STATUS_COMPLETED);
      requestEntity.setFinishedTime(Instant.now());
      documentLlmRequestRepository.update(requestEntity);

      assistantMessage.setAssistantText(providerResponse.assistantText());
      assistantMessage.setStatus(STATUS_COMPLETED);
      assistantMessage.setFinishReason(providerResponse.finishReason());
      assistantMessage.setProviderUsageJson(writeJson(providerResponse.usage()));
      assistantMessage.setProviderMetaJson(writeJson(filterProviderResponseMeta(providerResponse.providerResponseMeta())));
      assistantMessage.setErrorCode(null);
      documentLlmMessageRepository.update(assistantMessage);

      session.setUpdatedTime(Instant.now());
      documentLlmSessionRepository.update(session);
    } catch (LlmApiException exception) {
      if (executionRegistry.isCancelled(requestEntity.getRequestId())) {
        return;
      }
      requestEntity.setStatus(STATUS_FAILED);
      requestEntity.setFinishedTime(Instant.now());
      documentLlmRequestRepository.update(requestEntity);
      assistantMessage.setStatus(STATUS_FAILED);
      assistantMessage.setErrorCode(exception.errorCode());
      documentLlmMessageRepository.update(assistantMessage);
    } finally {
      executionRegistry.unregister(requestEntity.getRequestId());
    }
  }

  private void requireLlmEnabled() {
    if (!llmProperties.isFeatureEnabled() || !llmProperties.isEnabled()) {
      throw new LlmApiException(LlmErrorCodes.LLM_DISABLED, HttpStatus.SERVICE_UNAVAILABLE, "AI 工作台当前已禁用。");
    }
    if (!llmProperties.isConfigured()) {
      throw new LlmApiException(LlmErrorCodes.LLM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "当前未配置可用的模型 provider。");
    }
  }

  private LlmProviderStrategy requireProvider() {
    LlmProviderStrategy provider = resolveProvider();
    if (provider == null) {
      throw new LlmApiException(LlmErrorCodes.LLM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "未找到匹配的模型 provider 策略。");
    }
    return provider;
  }

  private LlmProviderStrategy resolveProvider() {
    return providerStrategies.stream()
        .filter(strategy -> strategy.providerName().equalsIgnoreCase(llmProperties.getProvider()))
        .findFirst()
        .orElse(null);
  }

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

  private Map<String, Object> filterProviderResponseMeta(Map<String, Object> providerResponseMeta) {
    if (providerResponseMeta == null || providerResponseMeta.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> filtered = new LinkedHashMap<>();
    for (String key : llmProperties.getProviderResponseMetaAllowlist()) {
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

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

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

  private Integer readInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }
}
