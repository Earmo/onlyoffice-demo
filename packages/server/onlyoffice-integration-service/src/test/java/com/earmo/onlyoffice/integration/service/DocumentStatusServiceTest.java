package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentEditorSessionRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentRuntimeEventRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.service.impl.DocumentStatusServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentStatusServiceTest {

  @Test
  void shouldPersistRuntimeEventsAndProjectRecentStatus() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
    DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
    when(metadataService.markOpened("demo")).thenReturn(status("demo", "draft"));
    when(metadataService.recordCallbackReceived("demo", 2)).thenReturn(status("demo", "editing"));
    when(metadataService.markSaved("demo", 2)).thenReturn(status("demo", "saved"));
    when(metadataService.markFailed("demo", 6, "下载新文件失败")).thenReturn(status("demo", "failed"));
    when(metadataService.getStatus("demo")).thenReturn(status("demo", "saved"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("save_succeeded", "最新修改已成功回写到共享存储。", 2)));

    DocumentStatusService service = new DocumentStatusServiceImpl(
        metadataService,
        runtimeEventRepository,
        documentEditorSessionRepository
    );

    assertEquals("draft", service.initialize("demo").state());
    assertEquals("editing", service.recordCallbackReceived("demo", 2).state());
    assertEquals("saved", service.recordSaveSucceeded("demo", 2).state());
    assertEquals("failed", service.recordSaveFailed("demo", 6, "下载新文件失败").state());
    DocumentSaveStatusResponse current = service.getStatus("demo");

    assertFalse(current.recentEvents().isEmpty());
    assertEquals("save_succeeded", current.recentEvents().get(0).eventType());
    verify(runtimeEventRepository, times(4))
        .save(org.mockito.ArgumentMatchers.any(DocumentRuntimeEventEntity.class));
  }

  @Test
  void shouldRecordRejectedCallbackAsIndependentRuntimeEvent() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
    DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
    when(metadataService.getStatus("demo")).thenReturn(status("demo", "editing"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("callback_rejected", "JWT 无效", null)));

    DocumentStatusService service = new DocumentStatusServiceImpl(
        metadataService,
        runtimeEventRepository,
        documentEditorSessionRepository
    );

    DocumentSaveStatusResponse current = service.recordCallbackRejected("demo", "JWT 无效");

    assertEquals("editing", current.state());
    assertEquals("callback_rejected", current.recentEvents().get(0).eventType());
    assertEquals("JWT 无效", current.recentEvents().get(0).message());
    verify(metadataService, never()).markFailed(any(), any(), any());
    verify(runtimeEventRepository).save(any(DocumentRuntimeEventEntity.class));
  }

  @Test
  void shouldOpenEditingSessionAndPersistActiveEditor() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
    DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
    when(metadataService.markEditingStarted("demo")).thenReturn(status("demo", "editing"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("editing_session_started", "编辑会话已建立。", null)));
    when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
        .thenReturn(Optional.empty());

    DocumentStatusService service = new DocumentStatusServiceImpl(
        metadataService,
        runtimeEventRepository,
        documentEditorSessionRepository
    );

    DocumentSaveStatusResponse current = service.openEditingSession("demo", accessContext());

    assertEquals("editing", current.state());
    assertEquals("editing_session_started", current.recentEvents().get(0).eventType());
    verify(documentEditorSessionRepository).insert(any(DocumentEditorSessionEntity.class));
    verify(runtimeEventRepository).save(any(DocumentRuntimeEventEntity.class));
  }

  @Test
  void shouldCloseEditingSessionAndReconcileStatusWhenNoActiveEditorsRemain() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
    DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
    when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
        .thenReturn(Optional.of(editorSession()));
    when(documentEditorSessionRepository.countActiveByDocumentId("demo")).thenReturn(0L);
    when(metadataService.reconcileClosedEditingSession("demo")).thenReturn(status("demo", "saved"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("editing_session_closed", "当前用户已离开编辑器，文档已退出活跃编辑状态。", null)));

    DocumentStatusService service = new DocumentStatusServiceImpl(
        metadataService,
        runtimeEventRepository,
        documentEditorSessionRepository
    );

    DocumentSaveStatusResponse current = service.closeEditingSession("demo", accessContext());

    assertEquals("saved", current.state());
    verify(documentEditorSessionRepository).update(any(DocumentEditorSessionEntity.class));
    verify(metadataService).reconcileClosedEditingSession("demo");
  }

  @Test
  void shouldKeepEditingStatusWhenOtherActiveEditorsStillExist() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
    DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
    when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
        .thenReturn(Optional.of(editorSession()));
    when(documentEditorSessionRepository.countActiveByDocumentId("demo")).thenReturn(2L);
    when(metadataService.getStatus("demo")).thenReturn(status("demo", "editing"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("editing_session_closed", "当前用户已离开编辑器，仍有其他活跃编辑用户。", null)));

    DocumentStatusService service = new DocumentStatusServiceImpl(
        metadataService,
        runtimeEventRepository,
        documentEditorSessionRepository
    );

    DocumentSaveStatusResponse current = service.closeEditingSession("demo", accessContext());

    assertEquals("editing", current.state());
    verify(metadataService, never()).reconcileClosedEditingSession(any());
    verify(metadataService).getStatus("demo");
  }

  @Test
  void shouldProjectActiveEditingCountsFromRepository() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
    DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
    when(documentEditorSessionRepository.countActiveByDocumentIds(List.of("doc-1", "doc-2")))
        .thenReturn(Map.of("doc-1", 2, "doc-2", 0));

    DocumentStatusService service = new DocumentStatusServiceImpl(
        metadataService,
        runtimeEventRepository,
        documentEditorSessionRepository
    );

    Map<String, Integer> counts = service.countActiveEditingSessions(List.of("doc-1", "doc-2"));

    assertEquals(2, counts.get("doc-1"));
    assertEquals(0, counts.get("doc-2"));
    verify(documentEditorSessionRepository).countActiveByDocumentIds(List.of("doc-1", "doc-2"));
  }

  private AccessContext accessContext() {
    return new AccessContext(
        "tenant-a",
        "native",
        "user-a",
        "Alice",
        Map.of("edit", true),
        "header"
    );
  }

  private DocumentSaveStatusResponse status(String documentId, String state) {
    return new DocumentSaveStatusResponse(documentId, state, state, null, null, null, List.of());
  }

  private DocumentEditorSessionEntity editorSession() {
    DocumentEditorSessionEntity entity = new DocumentEditorSessionEntity();
    entity.setSessionId("session-1");
    entity.setDocumentId("demo");
    entity.setTenantId("tenant-a");
    entity.setActorUser("user-a");
    entity.setActorName("Alice");
    entity.setOpenedTime(Instant.parse("2026-03-25T09:55:00Z"));
    entity.setLastSeenTime(Instant.parse("2026-03-25T10:00:00Z"));
    return entity;
  }

  private DocumentRuntimeEventEntity event(String eventType, String message, Integer callbackStatus) {
    DocumentRuntimeEventEntity entity = new DocumentRuntimeEventEntity();
    entity.setEventId("evt-1");
    entity.setDocumentId("demo");
    entity.setEventType(eventType);
    entity.setEventMessage(message);
    entity.setCallbackStatus(callbackStatus);
    entity.setEventTime(Instant.parse("2026-03-25T10:00:00Z"));
    return entity;
  }
}


