package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;

/**
 * 文档运行状态服务契约。
 *
 * <p>接口只定义编辑运行态相关能力：
 * 初始化状态、记录 callback/save 事件，以及对外投影 `save-status` 响应。
 */
public interface DocumentStatusService {

  DocumentSaveStatusResponse initialize(String documentId);

  DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus);

  DocumentSaveStatusResponse recordCallbackRejected(String documentId, String message);

  DocumentSaveStatusResponse recordSaveSucceeded(String documentId, Integer callbackStatus);

  DocumentSaveStatusResponse recordSaveFailed(String documentId, Integer callbackStatus, String failureReason);

  DocumentSaveStatusResponse getStatus(String documentId);
}
