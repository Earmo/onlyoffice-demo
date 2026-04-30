package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.AccessAuditEventEntity;
import com.earmo.onlyoffice.integration.data.repository.AccessAuditEventRepository;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 轻量访问审计服务默认实现。
 *
 * <p>当前阶段不追求完整操作日志平台，而是把关键业务节点稳定落到共享数据库：
 * 1. create/upload/import/editor-config 这些人类访问动作记录 actor 信息；
 * 2. callback 相关动作显式记成 system event，避免伪造“某个人点击了保存”；
 * 3. 统一在这里组装审计实体，避免 controller 或其他 service 各自拼字段。
 */
@Service
@RequiredArgsConstructor
public class AccessAuditServiceImpl implements AccessAuditService {

    private final AccessAuditEventRepository accessAuditEventRepository;

    /**
     * 记录显式创建文档成功事件。
     *
     * @param documentId    内部文档 ID。
     * @param accessContext 当前访问上下文。
     */
    @Override
    public void recordDocumentCreated(String documentId, AccessContext accessContext) {
        saveEvent(documentId, accessContext, "document_created", "success", "显式创建文档成功。");
    }

    /**
     * 记录上传本地文档成功事件。
     *
     * @param documentId    内部文档 ID。
     * @param accessContext 当前访问上下文。
     */
    @Override
    public void recordDocumentUploaded(String documentId, AccessContext accessContext) {
        saveEvent(documentId, accessContext, "document_uploaded", "success", "上传文档成功。");
    }

    /**
     * 记录远程导入文档成功事件。
     *
     * @param documentId    内部文档 ID。
     * @param accessContext 当前访问上下文。
     */
    @Override
    public void recordDocumentImported(String documentId, AccessContext accessContext) {
        saveEvent(documentId, accessContext, "document_imported", "success", "远程导入文档成功。");
    }

    /**
     * 记录文档归档成功事件。
     *
     * @param documentId    内部文档 ID。
     * @param accessContext 当前访问上下文。
     */
    @Override
    public void recordDocumentArchived(String documentId, AccessContext accessContext) {
        saveEvent(documentId, accessContext, "document_archived", "success", "文档已逻辑删除并归档。");
    }

    /**
     * 记录用户请求 editor-config 的事件。
     *
     * @param documentId    内部文档 ID。
     * @param accessContext 当前访问上下文。
     */
    @Override
    public void recordEditorConfigRequested(String documentId, AccessContext accessContext) {
        saveEvent(documentId, accessContext, "editor_config_requested", "success", "请求 editor-config。");
    }

    /**
     * 记录 ONLYOFFICE callback 到达事件。
     *
     * @param documentId     内部文档 ID。
     * @param callbackStatus ONLYOFFICE callback status 值。
     */
    @Override
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

    /**
     * 记录 ONLYOFFICE callback 被拒绝事件。
     *
     * @param documentId 内部文档 ID。
     * @param reason     拒绝原因。
     */
    @Override
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

    /**
     * 统一组装人类访问事件。
     *
     * <p>这样业务层只需要告诉审计服务“发生了什么”，
     * 不需要在每个 controller 或 service 里重复拼 tenant / source / actor 字段。
     *
     * @param documentId    内部文档 ID。
     * @param accessContext 当前访问上下文。
     * @param eventType     审计事件类型。
     * @param eventResult   审计事件结果。
     * @param message       审计事件说明。
     */
    private void saveEvent(String documentId, AccessContext accessContext, String eventType, String eventResult, String message) {
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

    /**
     * 生成审计事件主键。
     *
     * @return UUID 字符串形式的事件 ID。
     */
    private String buildEventId() {
        return UUID.randomUUID().toString();
    }
}
