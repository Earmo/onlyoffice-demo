package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentRuntimeEventRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.service.impl.DocumentStatusServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
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
    when(metadataService.markOpened("demo")).thenReturn(status("demo", "draft"));
    when(metadataService.recordCallbackReceived("demo", 2)).thenReturn(status("demo", "editing"));
    when(metadataService.markSaved("demo", 2)).thenReturn(status("demo", "saved"));
    when(metadataService.markFailed("demo", 6, "下载新文件失败")).thenReturn(status("demo", "failed"));
    when(metadataService.getStatus("demo")).thenReturn(status("demo", "saved"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("save_succeeded", "最新修改已成功回写到共享存储。", 2)));

    DocumentStatusService service = new DocumentStatusServiceImpl(metadataService, runtimeEventRepository);

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
    when(metadataService.getStatus("demo")).thenReturn(status("demo", "editing"));
    when(runtimeEventRepository.listRecentByDocumentId("demo", 5))
        .thenReturn(List.of(event("callback_rejected", "JWT 无效", null)));

    DocumentStatusService service = new DocumentStatusServiceImpl(metadataService, runtimeEventRepository);

    DocumentSaveStatusResponse current = service.recordCallbackRejected("demo", "JWT 无效");

    assertEquals("editing", current.state());
    assertEquals("callback_rejected", current.recentEvents().get(0).eventType());
    assertEquals("JWT 无效", current.recentEvents().get(0).message());
    verify(metadataService, never()).markFailed(any(), any(), any());
    verify(runtimeEventRepository).save(any(DocumentRuntimeEventEntity.class));
  }

  private DocumentSaveStatusResponse status(String documentId, String state) {
    return new DocumentSaveStatusResponse(documentId, state, state, null, null, null, List.of());
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


