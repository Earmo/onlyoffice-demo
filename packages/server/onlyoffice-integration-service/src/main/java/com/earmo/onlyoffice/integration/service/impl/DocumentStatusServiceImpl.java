package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentEditorSessionRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentRuntimeEventRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusEventResponse;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文档运行状态服务默认实现。
 *
 * <p>这里专门承接“运行事件流 + 摘要状态”的组合逻辑：
 * 1. 文档主表负责当前摘要状态；
 * 2. 运行事件表负责最近发生了哪些 callback/save 轨迹；
 * 3. 对外统一投影成编辑页可直接消费的 `save-status` 响应。
 */
@Service
@RequiredArgsConstructor
public class DocumentStatusServiceImpl implements DocumentStatusService {

  private static final int RECENT_EVENT_LIMIT = 5;

  private final DocumentMetadataService documentMetadataService;
  private final DocumentRuntimeEventRepository documentRuntimeEventRepository;
  private final DocumentEditorSessionRepository documentEditorSessionRepository;

  @Override
  public DocumentSaveStatusResponse initialize(String documentId) {
    DocumentSaveStatusResponse summary = documentMetadataService.markOpened(documentId);
    recordRuntimeEvent(documentId, "preview_opened", null, "已打开文档预览。");
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse openEditingSession(String documentId, AccessContext accessContext) {
    DocumentSaveStatusResponse summary = documentMetadataService.markEditingStarted(documentId);
    upsertEditingSession(documentId, accessContext);
    recordRuntimeEvent(documentId, "editing_session_started", null, "编辑会话已建立。");
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse closeEditingSession(String documentId, AccessContext accessContext) {
    if (!StringUtils.hasText(accessContext.actorUser())) {
      return mergeRecentEvents(documentMetadataService.getStatus(documentId));
    }

    closeEditingSessionRecord(documentId, accessContext.actorUser());
    long activeEditors = documentEditorSessionRepository.countActiveByDocumentId(documentId);
    DocumentSaveStatusResponse summary = activeEditors > 0
        ? documentMetadataService.getStatus(documentId)
        : documentMetadataService.reconcileClosedEditingSession(documentId);

    recordRuntimeEvent(
        documentId,
        "editing_session_closed",
        null,
        activeEditors > 0
            ? "当前用户已离开编辑器，仍有其他活跃编辑用户。"
            : "当前用户已离开编辑器，文档已退出活跃编辑状态。"
    );
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
    DocumentSaveStatusResponse summary = documentMetadataService.recordCallbackReceived(documentId, callbackStatus);
    recordRuntimeEvent(documentId, "callback_received", callbackStatus, "已收到 ONLYOFFICE 保存回调。");
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse recordCallbackRejected(String documentId, String message) {
    DocumentSaveStatusResponse summary = documentMetadataService.getStatus(documentId);
    recordRuntimeEvent(documentId, "callback_rejected", null, message);
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse recordSaveSucceeded(String documentId, Integer callbackStatus) {
    DocumentSaveStatusResponse summary = documentMetadataService.markSaved(documentId, callbackStatus);
    recordRuntimeEvent(documentId, "save_succeeded", callbackStatus, "最新修改已成功回写到共享存储。");
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse recordSaveFailed(String documentId, Integer callbackStatus, String failureReason) {
    DocumentSaveStatusResponse summary = documentMetadataService.markFailed(documentId, callbackStatus, failureReason);
    recordRuntimeEvent(
        documentId,
        "save_failed",
        callbackStatus,
        failureReason == null ? "回写共享存储失败。" : "回写共享存储失败：" + failureReason
    );
    return mergeRecentEvents(summary);
  }

  @Override
  public DocumentSaveStatusResponse getStatus(String documentId) {
    return mergeRecentEvents(documentMetadataService.getStatus(documentId));
  }

  @Override
  public Map<String, Integer> countActiveEditingSessions(List<String> documentIds) {
    return documentEditorSessionRepository.countActiveByDocumentIds(documentIds);
  }

  /**
   * 统一记录关键运行态事件。
   *
   * <p>Phase 5 已经约定这里只记录关键节点而不是完整版本中心，
   * 因此 eventType 只覆盖 editor_opened / callback_received / save_succeeded / save_failed / callback_rejected 等核心事件。
   */
  private void recordRuntimeEvent(String documentId, String eventType, Integer callbackStatus, String message) {
    DocumentRuntimeEventEntity entity = new DocumentRuntimeEventEntity();
    entity.setEventId(UUID.randomUUID().toString());
    entity.setDocumentId(documentId);
    entity.setEventType(eventType);
    entity.setCallbackStatus(callbackStatus);
    entity.setEventMessage(message);
    entity.setEventTime(Instant.now());
    documentRuntimeEventRepository.save(entity);
  }

  /**
   * 编辑会话按“文档 + 当前 actor”维度去重。
   *
   * <p>这样同一个用户重复刷新编辑页时不会无限新增活跃会话记录，
   * 但不同用户同时打开同一文档时仍能得到正确的活跃编辑人数。
   */
  private void upsertEditingSession(String documentId, AccessContext accessContext) {
    Instant now = Instant.now();
    DocumentEditorSessionEntity entity = documentEditorSessionRepository
        .findActiveByDocumentIdAndActorUser(documentId, accessContext.actorUser())
        .orElseGet(DocumentEditorSessionEntity::new);

    boolean isNewSession = !StringUtils.hasText(entity.getSessionId());
    if (isNewSession) {
      entity.setSessionId(UUID.randomUUID().toString());
      entity.setDocumentId(documentId);
      entity.setTenantId(accessContext.tenantId());
      entity.setActorUser(accessContext.actorUser());
      entity.setOpenedTime(now);
      entity.setCreatedTime(now);
    }

    entity.setActorName(accessContext.actorName());
    entity.setLastSeenTime(now);
    entity.setClosedTime(null);
    entity.setUpdatedTime(now);

    if (isNewSession) {
      documentEditorSessionRepository.insert(entity);
    } else {
      documentEditorSessionRepository.update(entity);
    }
  }

  private void closeEditingSessionRecord(String documentId, String actorUser) {
    documentEditorSessionRepository.findActiveByDocumentIdAndActorUser(documentId, actorUser)
        .ifPresent(session -> {
          Instant now = Instant.now();
          session.setClosedTime(now);
          session.setLastSeenTime(now);
          session.setUpdatedTime(now);
          documentEditorSessionRepository.update(session);
        });
  }

  /**
   * 重新投影摘要状态和最近事件。
   *
   * <p>这样列表页仍然只读主表摘要，而编辑页 `save-status` 可以额外看到最近几条关键事件，
   * 既保持运行态信息完整，也避免列表直接依赖事件流表。
   */
  private DocumentSaveStatusResponse mergeRecentEvents(DocumentSaveStatusResponse summary) {
    List<DocumentSaveStatusEventResponse> recentEvents = documentRuntimeEventRepository
        .listRecentByDocumentId(summary.documentId(), RECENT_EVENT_LIMIT)
        .stream()
        .map(event -> new DocumentSaveStatusEventResponse(
            event.getEventType(),
            event.getEventMessage(),
            event.getCallbackStatus(),
            event.getEventTime()
        ))
        .toList();

    return new DocumentSaveStatusResponse(
        summary.documentId(),
        summary.state(),
        summary.message(),
        summary.lastCallbackStatus(),
        summary.lastCallbackTime(),
        summary.lastSavedTime(),
        recentEvents
    );
  }
}
