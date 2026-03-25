package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentRuntimeEventRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusEventResponse;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文档保存状态门面，底层状态由共享元数据承接。
 */
@Service
@RequiredArgsConstructor
public class DocumentStatusService {

  private static final int RECENT_EVENT_LIMIT = 5;

  private final DocumentMetadataService documentMetadataService;
  private final DocumentRuntimeEventRepository documentRuntimeEventRepository;

  public DocumentSaveStatusResponse initialize(String documentId) {
    DocumentSaveStatusResponse summary = documentMetadataService.markOpened(documentId);
    recordRuntimeEvent(documentId, "editor_opened", null, "编辑器会话已打开。");
    return mergeRecentEvents(summary);
  }

  public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
    DocumentSaveStatusResponse summary = documentMetadataService.recordCallbackReceived(documentId, callbackStatus);
    recordRuntimeEvent(documentId, "callback_received", callbackStatus, "已收到 ONLYOFFICE 保存回调。");
    return mergeRecentEvents(summary);
  }

  public DocumentSaveStatusResponse recordCallbackRejected(String documentId, String message) {
    DocumentSaveStatusResponse summary = documentMetadataService.getStatus(documentId);
    recordRuntimeEvent(documentId, "callback_rejected", null, message);
    return mergeRecentEvents(summary);
  }

  public DocumentSaveStatusResponse recordSaveSucceeded(String documentId, Integer callbackStatus) {
    DocumentSaveStatusResponse summary = documentMetadataService.markSaved(documentId, callbackStatus);
    recordRuntimeEvent(documentId, "save_succeeded", callbackStatus, "最新修改已成功回写到共享存储。");
    return mergeRecentEvents(summary);
  }

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

  public DocumentSaveStatusResponse getStatus(String documentId) {
    return mergeRecentEvents(documentMetadataService.getStatus(documentId));
  }

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
   * 运行摘要状态和最近事件流分层保存：
   * 主表负责“当前状态”，事件表负责“最近发生过什么”。
   * 这里把二者重新投影成编辑页可直接消费的 save-status 响应。
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


