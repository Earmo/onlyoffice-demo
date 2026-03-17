package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DocumentStatusServiceTest {

  @Test
  void shouldTrackCallbackAndSaveLifecycle() {
    DocumentStatusService service = new DocumentStatusService();

    DocumentSaveStatusResponse idle = service.initialize("demo");
    assertEquals("idle", idle.state());
    assertNull(idle.lastSavedAt());

    DocumentSaveStatusResponse callbackReceived = service.recordCallbackReceived("demo", 2);
    assertEquals("callback-received", callbackReceived.state());
    assertEquals(2, callbackReceived.lastCallbackStatus());
    assertNotNull(callbackReceived.lastCallbackAt());

    DocumentSaveStatusResponse saved = service.recordSaveSucceeded("demo", 2);
    assertEquals("saved", saved.state());
    assertEquals(2, saved.lastCallbackStatus());
    assertNotNull(saved.lastSavedAt());
  }

  @Test
  void shouldExposeSaveFailureMessage() {
    DocumentStatusService service = new DocumentStatusService();

    DocumentSaveStatusResponse failed = service.recordSaveFailed("demo", 6, "下载新文件失败");

    assertEquals("save-failed", failed.state());
    assertEquals(6, failed.lastCallbackStatus());
    assertEquals("回写本地存储失败：下载新文件失败", failed.message());
  }
}
