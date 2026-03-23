package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentStatusServiceTest {

  @Test
  void shouldDelegateLifecycleToMetadataService() {
    DocumentMetadataService metadataService = mock(DocumentMetadataService.class);
    when(metadataService.markOpened("demo")).thenReturn(status("demo", "draft"));
    when(metadataService.recordCallbackReceived("demo", 2)).thenReturn(status("demo", "editing"));
    when(metadataService.markSaved("demo", 2)).thenReturn(status("demo", "saved"));
    when(metadataService.markFailed("demo", 6, "下载新文件失败")).thenReturn(status("demo", "failed"));

    DocumentStatusService service = new DocumentStatusService(metadataService);

    assertEquals("draft", service.initialize("demo").state());
    assertEquals("editing", service.recordCallbackReceived("demo", 2).state());
    assertEquals("saved", service.recordSaveSucceeded("demo", 2).state());
    assertEquals("failed", service.recordSaveFailed("demo", 6, "下载新文件失败").state());
  }

  private DocumentSaveStatusResponse status(String documentId, String state) {
    return new DocumentSaveStatusResponse(documentId, state, state, null, null, null);
  }
}


