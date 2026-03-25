package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.AccessAuditEventEntity;
import com.earmo.onlyoffice.integration.data.repository.AccessAuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 轻量访问审计服务。
 *
 * <p>当前阶段只记录关键路径事件，不引入完整操作日志体系。
 * 记录重点是：
 * 1. 哪个文档发生了什么；
 * 2. 事件来自哪个访问上下文 provider；
 * 3. callback 等系统动作要明确标成 `system event`，不能伪装成某个人类用户的直接操作。
 */
@Service
@RequiredArgsConstructor
public class AccessAuditService {

  private final AccessAuditEventRepository accessAuditEventRepository;

  public void recordDocumentCreated(String documentId, AccessContext accessContext) {
    saveEvent(documentId, accessContext, "document_created", "success", "显式创建文档成功。");
  }

  public void recordDocumentUploaded(String documentId, AccessContext accessContext) {
    saveEvent(documentId, accessContext, "document_uploaded", "success", "上传文档成功。");
  }

  public void recordDocumentImported(String documentId, AccessContext accessContext) {
    saveEvent(documentId, accessContext, "document_imported", "success", "远程导入文档成功。");
  }

  public void recordEditorConfigRequested(String documentId, AccessContext accessContext) {
    saveEvent(documentId, accessContext, "editor_config_requested", "success", "请求 editor-config。");
  }

  public void recordCallbackReceived(String documentId, Integer callbackStatus) {
    AccessAuditEventEntity entity = new AccessAuditEventEntity();
    entity.setEventId(buildEventId());
    entity.setDocumentId(documentId);
    entity.setTenantId("system");
    entity.setSourceSystem("onlyoffice");
    entity.setActorUser(null);
    entity.setActorName(null);
    entity.setEventType("callback_received");
    entity.setEventTime(Instant.now());
    entity.setEventSource("system");
    entity.setEventResult("success");
    entity.setMessage("收到 ONLYOFFICE callback，status=" + callbackStatus);
    accessAuditEventRepository.save(entity);
  }

  public void recordCallbackRejected(String documentId, String reason) {
    AccessAuditEventEntity entity = new AccessAuditEventEntity();
    entity.setEventId(buildEventId());
    entity.setDocumentId(documentId);
    entity.setTenantId("system");
    entity.setSourceSystem("onlyoffice");
    entity.setActorUser(null);
    entity.setActorName(null);
    entity.setEventType("callback_rejected");
    entity.setEventTime(Instant.now());
    entity.setEventSource("system");
    entity.setEventResult("rejected");
    entity.setMessage(reason);
    accessAuditEventRepository.save(entity);
  }

  private void saveEvent(
      String documentId,
      AccessContext accessContext,
      String eventType,
      String eventResult,
      String message
  ) {
    AccessAuditEventEntity entity = new AccessAuditEventEntity();
    entity.setEventId(buildEventId());
    entity.setDocumentId(documentId);
    entity.setTenantId(accessContext.tenantId());
    entity.setSourceSystem(accessContext.sourceSystem());
    entity.setActorUser(accessContext.actorUser());
    entity.setActorName(accessContext.actorName());
    entity.setEventType(eventType);
    entity.setEventTime(Instant.now());
    entity.setEventSource(accessContext.source());
    entity.setEventResult(eventResult);
    entity.setMessage(message);
    accessAuditEventRepository.save(entity);
  }

  private String buildEventId() {
    return UUID.randomUUID().toString();
  }
}
