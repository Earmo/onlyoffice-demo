package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.data.repository.DocumentMetadataRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 承载文档主数据与共享状态流转。
 *
 * <p>这个服务专门负责“元数据真相源”这一层：
 * 1. 明确文档是否存在、属于谁、来源于哪里；
 * 2. 统一维护 editing/saved/failed 等主状态；
 * 3. 为文件存储层提供可复用的主数据读取和转换能力。
 *
 * <p>Phase 7 之后，带业务语义的查询统一收口到 repository，service 只在 CRUD 落点上直接使用 mapper。
 */
@Service
@RequiredArgsConstructor
public class DocumentMetadataService {

  public static final String STATUS_DRAFT = "draft";
  public static final String STATUS_EDITING = "editing";
  public static final String STATUS_SAVED = "saved";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_ARCHIVED = "archived";

  private final DocumentMetadataMapper documentMetadataMapper;
  private final DocumentMetadataRepository documentMetadataRepository;

  @Transactional(readOnly = true)
  public Optional<DocumentMetadataEntity> findDocument(String documentId) {
    return Optional.ofNullable(documentMetadataMapper.selectOneById(documentId));
  }

  @Transactional(readOnly = true)
  public DocumentMetadataEntity requireDocument(String documentId) {
    return findDocument(documentId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  @Transactional(readOnly = true)
  public List<DocumentMetadataEntity> listDocuments(String tenantId) {
    return documentMetadataRepository.listByTenant(tenantId);
  }

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
   * <p>当前阶段大多数入口还没有单独的 owner 参数，因此会继续使用兼容重载。
   * 但从这个重载开始，service 层已经不再把 `owner=request.user` 当作唯一形态写死，
   * 后续接外部业务归属时可以直接传入稳定 owner，而不需要推翻整个建档链路。
   */
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
      if (mapped.isPresent()) {
        return mapped.get();
      }
    }

    return findDocument(documentId)
        .orElseGet(
            () -> saveNewDocument(
                documentId,
                title,
                fileType,
                documentType,
                storageKey,
                requestContext,
                ownerUser,
                externalDocumentId
            )
        );
  }

  @Transactional
  public DocumentSaveStatusResponse markOpened(String documentId) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setLastOpenedTime(now);
    if (!StringUtils.hasText(entity.getStatus())) {
      entity.setStatus(STATUS_DRAFT);
    }
    entity.setUpdatedTime(now);
    updateEntity(entity);
    return toSaveStatus(entity);
  }

  @Transactional
  public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setStatus(STATUS_EDITING);
    entity.setLastCallbackStatus(callbackStatus);
    entity.setLastCallbackTime(now);
    entity.setLastErrorMessage(null);
    entity.setUpdatedTime(now);
    updateEntity(entity);
    return toSaveStatus(entity);
  }

  @Transactional
  public DocumentSaveStatusResponse markSaved(String documentId, Integer callbackStatus) {
    DocumentMetadataEntity entity = requireDocument(documentId);
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

  @Transactional
  public DocumentSaveStatusResponse markFailed(String documentId, Integer callbackStatus, String message) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setStatus(STATUS_FAILED);
    entity.setLastCallbackStatus(callbackStatus);
    entity.setLastCallbackTime(entity.getLastCallbackTime() == null ? now : entity.getLastCallbackTime());
    entity.setLastErrorMessage(message);
    entity.setUpdatedTime(now);
    updateEntity(entity);
    return toSaveStatus(entity);
  }

  @Transactional(readOnly = true)
  public DocumentSaveStatusResponse getStatus(String documentId) {
    return toSaveStatus(requireDocument(documentId));
  }

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

  private DocumentSaveStatusResponse toSaveStatus(DocumentMetadataEntity entity) {
    return new DocumentSaveStatusResponse(
        entity.getDocumentId(),
        entity.getStatus(),
        buildStatusMessage(entity),
        entity.getLastCallbackStatus(),
        entity.getLastCallbackTime(),
        entity.getLastSavedTime()
    );
  }

  private String buildStatusMessage(DocumentMetadataEntity entity) {
    return switch (entity.getStatus()) {
      case STATUS_EDITING -> entity.getLastCallbackTime() == null
          ? "文档正在编辑中。"
          : "已收到 ONLYOFFICE 保存回调，正在处理最新版本。";
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

  private void insertEntity(DocumentMetadataEntity entity) {
    documentMetadataMapper.insert(entity);
  }

  private void updateEntity(DocumentMetadataEntity entity) {
    documentMetadataMapper.update(entity);
  }
}
