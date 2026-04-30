package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentEditorSessionRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentRuntimeEventRepository;
import com.earmo.onlyoffice.integration.model.response.DocumentSaveStatusEventResponse;
import com.earmo.onlyoffice.integration.model.response.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.service.impl.DocumentStatusServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocumentStatusServiceTest {

    @Test
    void shouldPublishReturnedStatusForInitializeCallbackAndSaveMutations() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(metadataService.markOpened("demo")).thenReturn(status("demo", "draft"));
        when(metadataService.recordCallbackReceived("demo", 2)).thenReturn(status("demo", "editing"));
        when(metadataService.markSaved("demo", 2)).thenReturn(status("demo", "saved"));
        when(metadataService.markFailed("demo", 6, "下载新文件失败")).thenReturn(status("demo", "failed"));
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("save_succeeded", "最新修改已成功回写到共享存储。", 2)));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse initialized = service.initialize("demo");
        DocumentSaveStatusResponse callbackReceived = service.recordCallbackReceived("demo", 2);
        DocumentSaveStatusResponse saveSucceeded = service.recordSaveSucceeded("demo", 2);
        DocumentSaveStatusResponse saveFailed = service.recordSaveFailed("demo", 6, "下载新文件失败");

        assertEquals("draft", initialized.state());
        assertEquals("editing", callbackReceived.state());
        assertEquals("saved", saveSucceeded.state());
        assertEquals("failed", saveFailed.state());
        assertFalse(saveFailed.recentEvents().isEmpty());
        assertEquals("save_succeeded", saveFailed.recentEvents().get(0).eventType());
        verify(runtimeEventRepository, times(4))
                .save(org.mockito.ArgumentMatchers.any(DocumentRuntimeEventEntity.class));
        verify(runtimeEventStreamService).publishSaveStatus("demo", initialized);
        verify(runtimeEventStreamService).publishSaveStatus("demo", callbackReceived);
        verify(runtimeEventStreamService).publishSaveStatus("demo", saveSucceeded);
        verify(runtimeEventStreamService).publishSaveStatus("demo", saveFailed);
    }

    @Test
    void shouldPublishReturnedStatusWhenClosedCallbackArrivesAfterEditorsLeft() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(metadataService.recordCallbackReceived("demo", 4)).thenReturn(status("demo", "editing"));
        when(metadataService.reconcileClosedEditingSession("demo")).thenReturn(status("demo", "saved"));
        when(documentEditorSessionRepository.countActiveByDocumentId(eq("demo"), any())).thenReturn(0L);
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("callback_received", "已收到 ONLYOFFICE 保存回调。", 4)));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse current = service.recordCallbackReceived("demo", 4);

        assertEquals("saved", current.state());
        verify(metadataService).recordCallbackReceived("demo", 4);
        verify(metadataService).reconcileClosedEditingSession("demo");
        verify(runtimeEventRepository).save(any(DocumentRuntimeEventEntity.class));
        verify(runtimeEventStreamService).publishSaveStatus("demo", current);
    }

    @Test
    void shouldPublishReturnedStatusForRejectedCallback() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(metadataService.getStatus("demo")).thenReturn(status("demo", "editing"));
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("callback_rejected", "JWT 无效", null)));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse current = service.recordCallbackRejected("demo", "JWT 无效");

        assertEquals("editing", current.state());
        assertEquals("callback_rejected", current.recentEvents().get(0).eventType());
        assertEquals("JWT 无效", current.recentEvents().get(0).message());
        verify(metadataService, never()).markFailed(any(), any(), any());
        verify(runtimeEventRepository).save(any(DocumentRuntimeEventEntity.class));
        verify(runtimeEventStreamService).publishSaveStatus("demo", current);
    }

    @Test
    void shouldPublishReturnedStatusWhenOpeningEditingSession() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(metadataService.markEditingStarted("demo")).thenReturn(status("demo", "editing"));
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("editing_session_started", "编辑会话已建立。", null)));
        when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
                .thenReturn(Optional.empty());

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse current = service.openEditingSession("demo", accessContext());

        assertEquals("editing", current.state());
        assertEquals("editing_session_started", current.recentEvents().get(0).eventType());
        verify(documentEditorSessionRepository).insert(any(DocumentEditorSessionEntity.class));
        verify(runtimeEventRepository).save(any(DocumentRuntimeEventEntity.class));
        verify(runtimeEventStreamService).publishSaveStatus("demo", current);
    }

    @Test
    void shouldPublishReturnedStatusWhenClosingEditingSessionWithoutActiveEditors() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
                .thenReturn(Optional.of(editorSession()));
        when(documentEditorSessionRepository.countActiveByDocumentId(eq("demo"), any())).thenReturn(0L);
        when(metadataService.reconcileClosedEditingSession("demo")).thenReturn(status("demo", "saved"));
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("editing_session_closed", "当前用户已离开编辑器，文档已退出活跃编辑状态。", null)));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse current = service.closeEditingSession("demo", accessContext());

        assertEquals("saved", current.state());
        verify(documentEditorSessionRepository).update(any(DocumentEditorSessionEntity.class));
        verify(metadataService).reconcileClosedEditingSession("demo");
        verify(runtimeEventStreamService).publishSaveStatus("demo", current);
    }

    @Test
    void shouldPublishReturnedStatusWhenClosingEditingSessionWithOtherEditorsStillActive() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
                .thenReturn(Optional.of(editorSession()));
        when(documentEditorSessionRepository.countActiveByDocumentId(eq("demo"), any())).thenReturn(2L);
        when(metadataService.getStatus("demo")).thenReturn(status("demo", "editing"));
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("editing_session_closed", "当前用户已离开编辑器，仍有其他活跃编辑用户。", null)));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse current = service.closeEditingSession("demo", accessContext());

        assertEquals("editing", current.state());
        verify(metadataService, never()).reconcileClosedEditingSession(any());
        verify(metadataService).getStatus("demo");
        verify(runtimeEventStreamService).publishSaveStatus("demo", current);
    }

    @Test
    void shouldProjectActiveEditingCountsFromRepository() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(documentEditorSessionRepository.countActiveByDocumentIds(eq(List.of("doc-1", "doc-2")), any()))
                .thenReturn(Map.of("doc-1", 2, "doc-2", 0));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        Map<String, Integer> counts = service.countActiveEditingSessions(List.of("doc-1", "doc-2"));

        assertEquals(2, counts.get("doc-1"));
        assertEquals(0, counts.get("doc-2"));
        verify(documentEditorSessionRepository).countActiveByDocumentIds(eq(List.of("doc-1", "doc-2")), any());
    }

    @Test
    void shouldRefreshEditingSessionHeartbeatForCurrentActor() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(documentEditorSessionRepository.findActiveByDocumentIdAndActorUser("demo", "user-a"))
                .thenReturn(Optional.of(editorSession()));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        service.touchEditingSession("demo", accessContext());

        verify(documentEditorSessionRepository).update(any(DocumentEditorSessionEntity.class));
        verify(runtimeEventRepository, never()).save(any(DocumentRuntimeEventEntity.class));
        verify(runtimeEventStreamService, never()).publishSaveStatus(any(), any());
    }

    @Test
    void shouldNotPublishWhenReadingProjectedStatus() {
        DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
        DocumentRuntimeEventRepository runtimeEventRepository = mock(DocumentRuntimeEventRepository.class);
        DocumentEditorSessionRepository documentEditorSessionRepository = mock(DocumentEditorSessionRepository.class);
        DocumentRuntimeEventStreamService runtimeEventStreamService = mock(DocumentRuntimeEventStreamService.class);
        when(metadataService.getStatus("demo")).thenReturn(status("demo", "saved"));
        when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
                .thenReturn(List.of(event("save_succeeded", "最新修改已成功回写到共享存储。", 2)));

        DocumentStatusService service = new DocumentStatusServiceImpl(
                properties(),
                metadataService,
                runtimeEventRepository,
                documentEditorSessionRepository,
                runtimeEventStreamService
        );

        DocumentSaveStatusResponse current = service.getStatus("demo");

        assertEquals("saved", current.state());
        assertEquals(
                List.of(new DocumentSaveStatusEventResponse(
                        "save_succeeded",
                        "最新修改已成功回写到共享存储。",
                        2,
                        Instant.parse("2026-03-25T10:00:00Z")
                )),
                current.recentEvents()
        );
        verify(runtimeEventStreamService, never()).publishSaveStatus(any(), any());
    }

    private OnlyofficeIntegrationProperties properties() {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
        properties.getEditingSession().setActiveTimeoutSeconds(30L);
        return properties;
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


