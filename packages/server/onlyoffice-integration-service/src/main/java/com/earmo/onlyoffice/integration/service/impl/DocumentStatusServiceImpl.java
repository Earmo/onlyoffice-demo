package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentEditorSessionRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentRuntimeEventRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusEventResponse;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentRuntimeEventStreamService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档运行状态服务默认实现。
 *
 * <p>这里专门承接“运行事件流 + 摘要状态”的组合逻辑：
 * 1. 文档主表负责当前摘要状态；
 * 2. 运行事件表负责最近发生了哪些 callback/save 轨迹；
 * 3. 对外统一投影成编辑页可直接消费的 `save-status` 响应。
 */
@Service
@RequiredArgsConstructor
public class DocumentStatusServiceImpl implements DocumentStatusService {

    private static final int RECENT_EVENT_LIMIT = 5;

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
    private final DocumentMetadataService documentMetadataService;
    private final DocumentRuntimeEventRepository documentRuntimeEventRepository;
    private final DocumentEditorSessionRepository documentEditorSessionRepository;
    private final DocumentRuntimeEventStreamService runtimeEventStreamService;

    /**
     * 初始化文档保存状态并记录预览打开事件。
     *
     * @param documentId 文档唯一标识
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse initialize(String documentId) {
        DocumentSaveStatusResponse summary = documentMetadataService.markOpened(documentId);
        recordRuntimeEvent(documentId, "preview_opened", null, "已打开文档预览。");
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 打开文档编辑会话并记录运行态事件。
     *
     * @param documentId    文档唯一标识
     * @param accessContext 访问上下文
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse openEditingSession(String documentId, AccessContext accessContext) {
        DocumentSaveStatusResponse summary = documentMetadataService.markEditingStarted(documentId);
        upsertEditingSession(documentId, accessContext);
        recordRuntimeEvent(documentId, "editing_session_started", null, "编辑会话已建立。");
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 关闭当前用户的文档编辑会话。
     *
     * @param documentId    文档唯一标识
     * @param accessContext 访问上下文
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse closeEditingSession(String documentId, AccessContext accessContext) {
        if (!StringUtils.hasText(accessContext.actorUser())) {
            return publishAndReturn(mergeRecentEvents(documentMetadataService.getStatus(documentId)));
        }

        closeEditingSessionRecord(documentId, accessContext.actorUser());
        long activeEditors = documentEditorSessionRepository.countActiveByDocumentId(documentId, activeSessionSince());
        DocumentSaveStatusResponse summary = activeEditors > 0
                ? documentMetadataService.getStatus(documentId)
                : documentMetadataService.reconcileClosedEditingSession(documentId);

        recordRuntimeEvent(
                documentId,
                "editing_session_closed",
                null,
                activeEditors > 0
                        ? "当前用户已离开编辑器，仍有其他活跃编辑用户。"
                        : "当前用户已离开编辑器，文档已退出活跃编辑状态。"
        );
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 记录 ONLYOFFICE 保存回调已到达。
     *
     * @param documentId     文档唯一标识
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
        DocumentSaveStatusResponse summary = documentMetadataService.recordCallbackReceived(documentId, callbackStatus);
        // ONLYOFFICE 在销毁最后一个编辑器连接后，可能还会补发 status=4 回调。
        // 如果当前已经没有任何活跃编辑会话，就不能继续把主状态留在 editing，
        // 否则列表页会在“会话已关闭”的情况下错误展示为“编辑中”。
        if (Integer.valueOf(4).equals(callbackStatus)
                && documentEditorSessionRepository.countActiveByDocumentId(documentId, activeSessionSince()) == 0) {
            summary = documentMetadataService.reconcileClosedEditingSession(documentId);
        }
        recordRuntimeEvent(documentId, "callback_received", callbackStatus, "已收到 ONLYOFFICE 保存回调。");
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 记录被拒绝的 ONLYOFFICE 回调。
     *
     * @param documentId 文档唯一标识
     * @param message    拒绝原因
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse recordCallbackRejected(String documentId, String message) {
        DocumentSaveStatusResponse summary = documentMetadataService.getStatus(documentId);
        recordRuntimeEvent(documentId, "callback_rejected", null, message);
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 记录文档保存成功。
     *
     * @param documentId     文档唯一标识
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse recordSaveSucceeded(String documentId, Integer callbackStatus) {
        DocumentSaveStatusResponse summary = documentMetadataService.markSaved(documentId, callbackStatus);
        recordRuntimeEvent(documentId, "save_succeeded", callbackStatus, "最新修改已成功回写到共享存储。");
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 记录文档保存失败。
     *
     * @param documentId     文档唯一标识
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @param failureReason  失败原因
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse recordSaveFailed(String documentId, Integer callbackStatus, String failureReason) {
        DocumentSaveStatusResponse summary = documentMetadataService.markFailed(documentId, callbackStatus, failureReason);
        recordRuntimeEvent(
                documentId,
                "save_failed",
                callbackStatus,
                failureReason == null ? "回写共享存储失败。" : "回写共享存储失败：" + failureReason
        );
        return publishAndReturn(mergeRecentEvents(summary));
    }

    /**
     * 查询文档当前保存状态。
     *
     * @param documentId 文档唯一标识
     * @return 合并最近事件后的保存状态响应
     */
    @Override
    public DocumentSaveStatusResponse getStatus(String documentId) {
        return mergeRecentEvents(documentMetadataService.getStatus(documentId));
    }

    /**
     * 刷新当前用户编辑会话的活跃时间。
     *
     * @param documentId    文档唯一标识
     * @param accessContext 访问上下文
     */
    @Override
    public void touchEditingSession(String documentId, AccessContext accessContext) {
        if (!StringUtils.hasText(accessContext.actorUser())) {
            return;
        }

        documentEditorSessionRepository.findActiveByDocumentIdAndActorUser(documentId, accessContext.actorUser())
                .ifPresent(session -> {
                    Instant now = Instant.now();
                    session.setActorName(accessContext.actorName());
                    session.setLastSeenTime(now);
                    session.setUpdatedTime(now);
                    documentEditorSessionRepository.update(session);
                });
    }

    /**
     * 统计多个文档的活跃编辑会话数量。
     *
     * @param documentIds 文档唯一标识列表
     * @return 文档 ID 到活跃会话数量的映射
     */
    @Override
    public Map<String, Integer> countActiveEditingSessions(List<String> documentIds) {
        return documentEditorSessionRepository.countActiveByDocumentIds(documentIds, activeSessionSince());
    }

    /**
     * 统一记录关键运行态事件。
     *
     * <p>Phase 5 已经约定这里只记录关键节点而不是完整版本中心，
     * 因此 eventType 只覆盖 editor_opened / callback_received / save_succeeded / save_failed / callback_rejected 等核心事件。
     *
     * @param documentId     文档唯一标识
     * @param eventType      运行态事件类型
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @param message        事件说明
     */
    private void recordRuntimeEvent(String documentId, String eventType, Integer callbackStatus, String message) {
        DocumentRuntimeEventEntity entity = new DocumentRuntimeEventEntity();
        entity.setEventId(UUID.randomUUID().toString());
        entity.setDocumentId(documentId);
        entity.setEventType(eventType);
        entity.setCallbackStatus(callbackStatus);
        entity.setEventMessage(message);
        entity.setEventTime(Instant.now());
        documentRuntimeEventRepository.save(entity);
    }

    /**
     * 编辑会话按“文档 + 当前 actor”维度去重。
     *
     * <p>这样同一个用户重复刷新编辑页时不会无限新增活跃会话记录，
     * 但不同用户同时打开同一文档时仍能得到正确的活跃编辑人数。
     *
     * @param documentId    文档唯一标识
     * @param accessContext 访问上下文
     */
    private void upsertEditingSession(String documentId, AccessContext accessContext) {
        Instant now = Instant.now();
        DocumentEditorSessionEntity entity = documentEditorSessionRepository
                .findActiveByDocumentIdAndActorUser(documentId, accessContext.actorUser())
                .orElseGet(DocumentEditorSessionEntity::new);

        boolean isNewSession = !StringUtils.hasText(entity.getSessionId());
        if (isNewSession) {
            entity.setSessionId(UUID.randomUUID().toString());
            entity.setDocumentId(documentId);
            entity.setTenantId(accessContext.tenantId());
            entity.setActorUser(accessContext.actorUser());
            entity.setOpenedTime(now);
            entity.setCreatedTime(now);
        }

        entity.setActorName(accessContext.actorName());
        entity.setLastSeenTime(now);
        entity.setClosedTime(null);
        entity.setUpdatedTime(now);

        if (isNewSession) {
            documentEditorSessionRepository.insert(entity);
        } else {
            documentEditorSessionRepository.update(entity);
        }
    }

    /**
     * 关闭当前用户的编辑会话记录。
     *
     * @param documentId 文档唯一标识
     * @param actorUser  当前操作者标识
     */
    private void closeEditingSessionRecord(String documentId, String actorUser) {
        documentEditorSessionRepository.findActiveByDocumentIdAndActorUser(documentId, actorUser)
                .ifPresent(session -> {
                    Instant now = Instant.now();
                    session.setClosedTime(now);
                    session.setLastSeenTime(now);
                    session.setUpdatedTime(now);
                    documentEditorSessionRepository.update(session);
                });
    }

    /**
     * 计算仍视为活跃编辑会话的最早心跳时间。
     *
     * @return 活跃会话时间下界
     */
    private Instant activeSessionSince() {
        long timeoutSeconds = Math.max(5L, onlyofficeIntegrationProperties.getEditingSession().getActiveTimeoutSeconds());
        return Instant.now().minusSeconds(timeoutSeconds);
    }

    /**
     * 重新投影摘要状态和最近事件。
     *
     * <p>这样列表页仍然只读主表摘要，而编辑页 `save-status` 可以额外看到最近几条关键事件，
     * 既保持运行态信息完整，也避免列表直接依赖事件流表。
     *
     * @param summary 主表保存状态摘要
     * @return 合并最近事件后的保存状态响应
     */
    private DocumentSaveStatusResponse mergeRecentEvents(DocumentSaveStatusResponse summary) {
        List<DocumentSaveStatusEventResponse> recentEvents = documentRuntimeEventRepository
                .listRecentByDocumentId(summary.documentId(), RECENT_EVENT_LIMIT)
                .stream()
                .map(event -> new DocumentSaveStatusEventResponse(
                        event.getEventType(),
                        event.getEventMessage(),
                        event.getCallbackStatus(),
                        event.getEventTime()
                ))
                .toList();

        return new DocumentSaveStatusResponse(
                summary.documentId(),
                summary.state(),
                summary.message(),
                summary.lastCallbackStatus(),
                summary.lastCallbackTime(),
                summary.lastSavedTime(),
                recentEvents
        );
    }

    /**
     * 广播保存状态并返回同一个状态对象。
     *
     * @param status 保存状态响应
     * @return 原始保存状态响应
     */
    private DocumentSaveStatusResponse publishAndReturn(DocumentSaveStatusResponse status) {
        // 14.1 这里故意不做任何 DTO 转换：
        // - REST `save-status` 接口返回的就是这个对象；
        // - SSE `save-status` 事件发的也是这个对象；
        // - controller 调用链拿到的还是这个对象。
        //
        // 这样做的目的，是把“当前文档保存状态”的事实源收束成一份。
        // 否则最容易出现三种漂移：
        // 1. 轮询接口字段改了，SSE 忘了改；
        // 2. controller 手拼 message，SSE 还是旧文案；
        // 3. 某个 mutation 返回 editing，但 SSE 推的是 saved。
        //
        // publishAndReturn 的意思就是：
        // “先把这个最终状态广播出去，再把同一个对象原样还给调用方”。
        runtimeEventStreamService.publishSaveStatus(status.documentId(), status);
        return status;
    }
}
