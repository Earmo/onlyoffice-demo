package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文档保存状态门面，底层状态由共享元数据承接。
 */
@Service
@RequiredArgsConstructor
public class DocumentStatusService {

  private final DocumentMetadataService documentMetadataService;

  public DocumentSaveStatusResponse initialize(String documentId) {
    return documentMetadataService.markOpened(documentId);
  }

  public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
    return documentMetadataService.recordCallbackReceived(documentId, callbackStatus);
  }

  public DocumentSaveStatusResponse recordSaveSucceeded(String documentId, Integer callbackStatus) {
    return documentMetadataService.markSaved(documentId, callbackStatus);
  }

  public DocumentSaveStatusResponse recordSaveFailed(String documentId, Integer callbackStatus, String failureReason) {
    return documentMetadataService.markFailed(documentId, callbackStatus, failureReason);
  }

  public DocumentSaveStatusResponse getStatus(String documentId) {
    return documentMetadataService.getStatus(documentId);
  }
}
