package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.CurrentAccessContext;

/**
 * 访问审计服务契约。
 *
 * <p>接口层只表达“哪些关键动作需要留下审计痕迹”，
 * 具体如何落库、如何组织 system event 语义，交给默认实现处理。
 */
public interface AccessAuditService {

  default void recordDocumentCreated(String documentId) {
    recordDocumentCreated(documentId, CurrentAccessContext.getRequired());
  }

  void recordDocumentCreated(String documentId, AccessContext accessContext);

  default void recordDocumentUploaded(String documentId) {
    recordDocumentUploaded(documentId, CurrentAccessContext.getRequired());
  }

  void recordDocumentUploaded(String documentId, AccessContext accessContext);

  default void recordDocumentImported(String documentId) {
    recordDocumentImported(documentId, CurrentAccessContext.getRequired());
  }

  void recordDocumentImported(String documentId, AccessContext accessContext);

  default void recordDocumentArchived(String documentId) {
    recordDocumentArchived(documentId, CurrentAccessContext.getRequired());
  }

  void recordDocumentArchived(String documentId, AccessContext accessContext);

  default void recordEditorConfigRequested(String documentId) {
    recordEditorConfigRequested(documentId, CurrentAccessContext.getRequired());
  }

  void recordEditorConfigRequested(String documentId, AccessContext accessContext);

  void recordCallbackReceived(String documentId, Integer callbackStatus);

  void recordCallbackRejected(String documentId, String reason);
}
