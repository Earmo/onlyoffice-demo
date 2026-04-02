package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;

/**
 * 访问审计服务契约。
 *
 * <p>接口层只表达“哪些关键动作需要留下审计痕迹”，
 * 具体如何落库、如何组织 system event 语义，交给默认实现处理。
 */
public interface AccessAuditService {

  void recordDocumentCreated(String documentId, AccessContext accessContext);

  void recordDocumentUploaded(String documentId, AccessContext accessContext);

  void recordDocumentImported(String documentId, AccessContext accessContext);

  void recordDocumentArchived(String documentId, AccessContext accessContext);

  void recordEditorConfigRequested(String documentId, AccessContext accessContext);

  void recordCallbackReceived(String documentId, Integer callbackStatus);

  void recordCallbackRejected(String documentId, String reason);
}
