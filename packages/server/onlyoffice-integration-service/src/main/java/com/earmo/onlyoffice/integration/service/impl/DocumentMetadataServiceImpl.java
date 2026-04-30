package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.data.repository.DocumentMetadataRepository;
import com.earmo.onlyoffice.integration.model.response.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.exception.DocumentNotFoundException;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档主数据服务默认实现。
 *
 * <p>这里专门处理“元数据真相源”层的职责：
 * 1. 决定文档是否存在、属于哪个租户、归属哪个 owner；
 * 2. 维护 `draft/editing/saved/failed/archived` 这些主表摘要状态；
 * 3. 把底层实体重新投影成上层可消费的 `StoredDocument` 和 `DocumentSaveStatusResponse`。
 *
 * <p>与运行事件流不同，这里只保存“当前摘要”，不会把最近事件历史强行塞回主表。
 */
@Service
@RequiredArgsConstructor
public class DocumentMetadataServiceImpl implements DocumentMetadataService {

    private final DocumentMetadataMapper documentMetadataMapper;
    private final DocumentMetadataRepository documentMetadataRepository;

    /**
     * 根据文档 ID 查询文档元数据。
     *
     * @param documentId 文档唯一标识
     * @return 文档元数据可选值，未找到时为空
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentMetadataEntity> findDocument(String documentId) {
        return Optional.ofNullable(documentMetadataMapper.selectOneById(documentId));
    }

    /**
     * 根据文档 ID 查询文档元数据，未找到时抛出业务异常。
     *
     * @param documentId 文档唯一标识
     * @return 文档元数据实体
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentMetadataEntity requireDocument(String documentId) {
        return findDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    /**
     * 查询可访问的文档元数据，已归档文档按不存在处理。
     *
     * @param documentId 文档唯一标识
     * @return 可访问的文档元数据实体
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentMetadataEntity requireAccessibleDocument(String documentId) {
        DocumentMetadataEntity entity = requireDocument(documentId);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw new DocumentNotFoundException(documentId);
        }
        return entity;
    }

    /**
     * 查询租户下全部可见文档。
     *
     * @param tenantId 租户标识
     * @return 文档元数据列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadataEntity> listDocuments(String tenantId) {
        return documentMetadataRepository.listVisibleByTenant(tenantId, null, null, null, null, "desc");
    }

    /**
     * 工作台首页使用的列表查询入口。
     *
     * <p>这里坚持只做最小可交付筛选：
     * query / status / sourceSystem / documentType / sortDirection。
     * 更复杂的全文检索或多维统计不是这一层的目标。
     *
     * @param tenantId      租户标识
     * @param query         标题或业务标识搜索关键词
     * @param status        文档状态筛选
     * @param sourceSystem  来源系统筛选
     * @param documentType  文档类型筛选
     * @param sortDirection 排序方向
     * @return 文档元数据列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadataEntity> listDocuments(
            String tenantId,
            String query,
            String status,
            String sourceSystem,
            String documentType,
            String sortDirection
    ) {
        return documentMetadataRepository.listVisibleByTenant(
                tenantId,
                query,
                status,
                sourceSystem,
                documentType,
                sortDirection
        );
    }

    /**
     * 分页查询租户下可见文档。
     *
     * @param tenantId      租户标识
     * @param query         标题或业务标识搜索关键词
     * @param status        文档状态筛选
     * @param sourceSystem  来源系统筛选
     * @param documentType  文档类型筛选
     * @param sortDirection 排序方向
     * @param pageNumber    页码，从 1 开始
     * @param pageSize      每页数量
     * @return 文档元数据分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public Page<DocumentMetadataEntity> listDocumentPage(
            String tenantId,
            String query,
            String status,
            String sourceSystem,
            String documentType,
            String sortDirection,
            int pageNumber,
            int pageSize
    ) {
        return documentMetadataRepository.paginateVisibleByTenant(
                tenantId,
                query,
                status,
                sourceSystem,
                documentType,
                sortDirection,
                pageNumber,
                pageSize
        );
    }

    /**
     * 查询租户最近访问或更新的文档。
     *
     * @param tenantId 租户标识
     * @param limit    返回数量上限
     * @return 最近文档元数据列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadataEntity> listRecentDocuments(String tenantId, int limit) {
        return documentMetadataRepository.listRecentVisibleByTenant(tenantId, limit);
    }

    /**
     * 创建文档元数据，并默认使用请求上下文中的 ownerUser 作为文档归属人。
     *
     * @param documentId         文档唯一标识
     * @param title              文档标题
     * @param fileType           文件扩展类型
     * @param documentType       ONLYOFFICE 文档类型
     * @param storageKey         存储对象键
     * @param requestContext     请求上下文
     * @param externalDocumentId 外部系统文档标识
     * @return 已存在或新创建的文档元数据
     */
    @Override
    @Transactional
    public DocumentMetadataEntity createDocument(
            String documentId,
            String title,
            String fileType,
            String documentType,
            String storageKey,
            RequestContext requestContext,
            String externalDocumentId
    ) {
        return createDocument(
                documentId,
                title,
                fileType,
                documentType,
                storageKey,
                requestContext,
                requestContext.ownerUser(),
                externalDocumentId
        );
    }

    /**
     * 显式区分“文档归属 owner”和“当前操作者 actor”。
     *
     * <p>当前大多数入口仍会把 owner 回退成请求中的 ownerUser，
     * 但接口已经允许后续外部系统把稳定归属单独传进来，不再强制 `owner = actor`。
     *
     * @param documentId         文档唯一标识
     * @param title              文档标题
     * @param fileType           文件扩展类型
     * @param documentType       ONLYOFFICE 文档类型
     * @param storageKey         存储对象键
     * @param requestContext     请求上下文
     * @param ownerUser          文档归属用户
     * @param externalDocumentId 外部系统文档标识
     * @return 已存在或新创建的文档元数据
     */
    @Override
    @Transactional
    public DocumentMetadataEntity createDocument(
            String documentId,
            String title,
            String fileType,
            String documentType,
            String storageKey,
            RequestContext requestContext,
            String ownerUser,
            String externalDocumentId
    ) {
        if (StringUtils.hasText(externalDocumentId)) {
            Optional<DocumentMetadataEntity> mapped = documentMetadataRepository.findBySourceSystemAndExternalDocument(
                    requestContext.sourceSystem(),
                    externalDocumentId
            );
            if (mapped.isPresent() && !STATUS_ARCHIVED.equals(mapped.get().getStatus())) {
                return mapped.get();
            }
        }

        return findDocument(documentId)
                .filter(entity -> !STATUS_ARCHIVED.equals(entity.getStatus()))
                .orElseGet(() -> saveNewDocument(
                        documentId,
                        title,
                        fileType,
                        documentType,
                        storageKey,
                        requestContext,
                        ownerUser,
                        externalDocumentId
                ));
    }

    /**
     * 将文档标记为归档状态。
     *
     * @param documentId 文档唯一标识
     * @return 归档后的文档元数据
     */
    @Override
    @Transactional
    public DocumentMetadataEntity archiveDocument(String documentId) {
        DocumentMetadataEntity entity = requireDocument(documentId);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            return entity;
        }

        entity.setStatus(STATUS_ARCHIVED);
        entity.setUpdatedTime(Instant.now());
        updateEntity(entity);
        return entity;
    }

    /**
     * 标记文档已被打开预览。
     *
     * @param documentId 文档唯一标识
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional
    public DocumentSaveStatusResponse markOpened(String documentId) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        Instant now = Instant.now();
        if (!STATUS_EDITING.equals(entity.getStatus()) || entity.getLastOpenedTime() == null) {
            entity.setLastOpenedTime(now);
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        entity.setUpdatedTime(now);
        updateEntity(entity);
        return toSaveStatus(entity);
    }

    /**
     * 标记文档进入编辑会话。
     *
     * @param documentId 文档唯一标识
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional
    public DocumentSaveStatusResponse markEditingStarted(String documentId) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        Instant now = Instant.now();
        if (!STATUS_EDITING.equals(entity.getStatus()) || entity.getLastOpenedTime() == null) {
            // 只有在“从非编辑态进入编辑态”时，才开启新一轮编辑会话批次时间。
            // 同一轮协同编辑里重复请求 editor-config 不应刷新它，否则会把当前 key 切掉。
            entity.setLastOpenedTime(now);
        }
        entity.setStatus(STATUS_EDITING);
        entity.setUpdatedTime(now);
        updateEntity(entity);
        return toSaveStatus(entity);
    }

    /**
     * 记录已收到 ONLYOFFICE 保存回调。
     *
     * @param documentId     文档唯一标识
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional
    public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        Instant now = Instant.now();
        entity.setStatus(STATUS_EDITING);
        entity.setLastCallbackStatus(callbackStatus);
        entity.setLastCallbackTime(now);
        entity.setLastErrorMessage(null);
        entity.setUpdatedTime(now);
        updateEntity(entity);
        return toSaveStatus(entity);
    }

    /**
     * 标记文档保存成功。
     *
     * @param documentId     文档唯一标识
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional
    public DocumentSaveStatusResponse markSaved(String documentId, Integer callbackStatus) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        Instant now = Instant.now();
        entity.setStatus(STATUS_SAVED);
        entity.setLastCallbackStatus(callbackStatus);
        entity.setLastCallbackTime(entity.getLastCallbackTime() == null ? now : entity.getLastCallbackTime());
        entity.setLastSavedTime(now);
        entity.setLastErrorMessage(null);
        entity.setUpdatedTime(now);
        updateEntity(entity);
        return toSaveStatus(entity);
    }

    /**
     * 更新文档展示标题和格式信息。
     *
     * @param documentId   文档唯一标识
     * @param title        文档标题
     * @param fileType     文件扩展类型
     * @param documentType ONLYOFFICE 文档类型
     * @return 更新后的文档元数据
     */
    @Override
    @Transactional
    public DocumentMetadataEntity updateDocumentFormat(String documentId, String title, String fileType, String documentType) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        entity.setTitle(title);
        entity.setFileType(fileType);
        entity.setDocumentType(documentType);
        entity.setUpdatedTime(Instant.now());
        updateEntity(entity);
        return entity;
    }

    /**
     * 标记文档保存失败。
     *
     * @param documentId     文档唯一标识
     * @param callbackStatus ONLYOFFICE 回调状态码
     * @param message        失败原因
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional
    public DocumentSaveStatusResponse markFailed(String documentId, Integer callbackStatus, String message) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        Instant now = Instant.now();
        entity.setStatus(STATUS_FAILED);
        entity.setLastCallbackStatus(callbackStatus);
        entity.setLastCallbackTime(entity.getLastCallbackTime() == null ? now : entity.getLastCallbackTime());
        entity.setLastErrorMessage(message);
        entity.setUpdatedTime(now);
        updateEntity(entity);
        return toSaveStatus(entity);
    }

    /**
     * 在最后一个编辑会话关闭后重新收口文档状态。
     *
     * @param documentId 文档唯一标识
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional
    public DocumentSaveStatusResponse reconcileClosedEditingSession(String documentId) {
        DocumentMetadataEntity entity = requireAccessibleDocument(documentId);
        if (!STATUS_EDITING.equals(entity.getStatus())) {
            return toSaveStatus(entity);
        }

        entity.setStatus(resolveStatusWithoutActiveEditors(entity));
        entity.setUpdatedTime(Instant.now());
        updateEntity(entity);
        return toSaveStatus(entity);
    }

    /**
     * 查询文档当前保存状态。
     *
     * @param documentId 文档唯一标识
     * @return 当前保存状态摘要
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentSaveStatusResponse getStatus(String documentId) {
        return toSaveStatus(requireAccessibleDocument(documentId));
    }

    /**
     * 将元数据实体和存储文件信息投影为对外文档模型。
     *
     * @param entity       文档元数据实体
     * @param path         文档本地或临时路径
     * @param lastModified 文档最后修改时间
     * @return 对外存储文档模型
     */
    @Override
    public StoredDocument toStoredDocument(DocumentMetadataEntity entity, Path path, Instant lastModified) {
        return new StoredDocument(
                entity.getDocumentId(),
                entity.getTenantId(),
                entity.getOwnerUser(),
                entity.getSourceSystem(),
                entity.getExternalDocumentId(),
                entity.getTitle(),
                entity.getStorageKey(),
                entity.getFileType(),
                entity.getDocumentType(),
                entity.getStatus(),
                path,
                lastModified,
                entity.getLastOpenedTime(),
                entity.getLastSavedTime(),
                entity.getLastCallbackStatus(),
                entity.getLastErrorMessage()
        );
    }

    /**
     * 创建并写入新的文档元数据实体。
     *
     * @param documentId         文档唯一标识
     * @param title              文档标题
     * @param fileType           文件扩展类型
     * @param documentType       ONLYOFFICE 文档类型
     * @param storageKey         存储对象键
     * @param requestContext     请求上下文
     * @param ownerUser          文档归属用户
     * @param externalDocumentId 外部系统文档标识
     * @return 新建的文档元数据实体
     */
    private DocumentMetadataEntity saveNewDocument(
            String documentId,
            String title,
            String fileType,
            String documentType,
            String storageKey,
            RequestContext requestContext,
            String ownerUser,
            String externalDocumentId
    ) {
        Instant now = Instant.now();
        DocumentMetadataEntity entity = new DocumentMetadataEntity();
        entity.setDocumentId(documentId);
        entity.setTenantId(requestContext.tenantId());
        entity.setOwnerUser(ownerUser);
        entity.setSourceSystem(requestContext.sourceSystem());
        entity.setExternalDocumentId(externalDocumentId);
        entity.setTitle(title);
        entity.setStorageKey(storageKey);
        entity.setFileType(fileType);
        entity.setDocumentType(documentType);
        entity.setStatus(STATUS_DRAFT);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        insertEntity(entity);
        return entity;
    }

    /**
     * 将文档元数据投影为保存状态摘要。
     *
     * @param entity 文档元数据实体
     * @return 保存状态响应
     */
    private DocumentSaveStatusResponse toSaveStatus(DocumentMetadataEntity entity) {
        return new DocumentSaveStatusResponse(
                entity.getDocumentId(),
                entity.getStatus(),
                buildStatusMessage(entity),
                entity.getLastCallbackStatus(),
                entity.getLastCallbackTime(),
                entity.getLastSavedTime(),
                List.of()
        );
    }

    /**
     * 根据文档摘要状态生成用户可读状态文案。
     *
     * @param entity 文档元数据实体
     * @return 状态说明文案
     */
    private String buildStatusMessage(DocumentMetadataEntity entity) {
        return switch (entity.getStatus()) {
            case STATUS_EDITING -> shouldDescribeCallbackProcessing(entity)
                    ? "已收到 ONLYOFFICE 保存回调，正在处理最新版本。"
                    : "文档当前存在活跃编辑会话。";
            case STATUS_SAVED -> "最新修改已成功回写到共享存储。";
            case STATUS_FAILED -> {
                if (StringUtils.hasText(entity.getLastErrorMessage())) {
                    yield "回写共享存储失败：" + entity.getLastErrorMessage();
                }
                yield "回写共享存储失败。";
            }
            case STATUS_ARCHIVED -> "文档已归档。";
            default -> "文档已创建，尚未进入编辑会话。";
        };
    }

    /**
     * 关闭最后一个活跃编辑会话后，需要把主表 `editing` 收口回稳定摘要状态。
     *
     * <p>优先级保持简单且可解释：
     * 1. 有失败信息时回到 `failed`；
     * 2. 有成功保存记录时回到 `saved`；
     * 3. 其余情况回到 `draft`。
     *
     * @param entity 文档元数据实体
     * @return 无活跃编辑者时应回落到的摘要状态
     */
    private String resolveStatusWithoutActiveEditors(DocumentMetadataEntity entity) {
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            return STATUS_ARCHIVED;
        }
        if (StringUtils.hasText(entity.getLastErrorMessage())) {
            return STATUS_FAILED;
        }
        if (entity.getLastSavedTime() != null) {
            return STATUS_SAVED;
        }
        return STATUS_DRAFT;
    }

    /**
     * 判断编辑态文案是否应提示回调正在处理。
     *
     * @param entity 文档元数据实体
     * @return 需要提示回调处理中时返回 true
     */
    private boolean shouldDescribeCallbackProcessing(DocumentMetadataEntity entity) {
        return entity.getLastCallbackTime() != null
                && (entity.getLastSavedTime() == null || entity.getLastSavedTime().isBefore(entity.getLastCallbackTime()));
    }

    /**
     * 插入文档元数据实体。
     *
     * @param entity 文档元数据实体
     */
    private void insertEntity(DocumentMetadataEntity entity) {
        documentMetadataMapper.insert(entity);
    }

    /**
     * 更新文档元数据实体。
     *
     * @param entity 文档元数据实体
     */
    private void updateEntity(DocumentMetadataEntity entity) {
        documentMetadataMapper.update(entity);
    }

}
