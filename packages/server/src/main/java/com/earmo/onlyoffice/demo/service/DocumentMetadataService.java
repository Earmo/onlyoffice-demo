package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataEntity;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataMapper;
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
 * <p>在从 JPA 切到 MyBatis-Flex 后，原来由实体生命周期回调自动完成的时间戳维护，
 * 现在集中放回 service 中显式处理，这样实现步骤更清晰，也更容易追踪每次状态更新的副作用。
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
    return documentMetadataMapper.selectByTenantIdOrderByUpdatedAtDesc(tenantId);
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
    if (StringUtils.hasText(externalDocumentId)) {
      Optional<DocumentMetadataEntity> mapped = Optional.ofNullable(
          documentMetadataMapper.selectBySourceSystemAndExternalDocumentId(
              requestContext.sourceSystem(),
              externalDocumentId
          )
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
                externalDocumentId
            )
        );
  }

  @Transactional
  public DocumentSaveStatusResponse markOpened(String documentId) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setLastOpenedAt(Instant.now());
    if (!StringUtils.hasText(entity.getStatus())) {
      entity.setStatus(STATUS_DRAFT);
    }
    entity.setUpdatedAt(now);
    updateEntity(entity);
    return toSaveStatus(entity);
  }

  @Transactional
  public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setStatus(STATUS_EDITING);
    entity.setLastCallbackStatus(callbackStatus);
    entity.setLastCallbackAt(now);
    entity.setLastErrorMessage(null);
    entity.setUpdatedAt(now);
    updateEntity(entity);
    return toSaveStatus(entity);
  }

  @Transactional
  public DocumentSaveStatusResponse markSaved(String documentId, Integer callbackStatus) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setStatus(STATUS_SAVED);
    entity.setLastCallbackStatus(callbackStatus);
    entity.setLastCallbackAt(entity.getLastCallbackAt() == null ? now : entity.getLastCallbackAt());
    entity.setLastSavedAt(now);
    entity.setLastErrorMessage(null);
    entity.setUpdatedAt(now);
    updateEntity(entity);
    return toSaveStatus(entity);
  }

  @Transactional
  public DocumentSaveStatusResponse markFailed(String documentId, Integer callbackStatus, String message) {
    DocumentMetadataEntity entity = requireDocument(documentId);
    Instant now = Instant.now();
    entity.setStatus(STATUS_FAILED);
    entity.setLastCallbackStatus(callbackStatus);
    entity.setLastCallbackAt(entity.getLastCallbackAt() == null ? now : entity.getLastCallbackAt());
    entity.setLastErrorMessage(message);
    entity.setUpdatedAt(now);
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
        entity.getOwnerUserId(),
        entity.getSourceSystem(),
        entity.getExternalDocumentId(),
        entity.getTitle(),
        entity.getStorageKey(),
        entity.getFileType(),
        entity.getDocumentType(),
        entity.getStatus(),
        path,
        lastModified,
        entity.getLastOpenedAt(),
        entity.getLastSavedAt(),
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
      String externalDocumentId
  ) {
    Instant now = Instant.now();
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId(documentId);
    entity.setTenantId(requestContext.tenantId());
    entity.setOwnerUserId(requestContext.ownerUserId());
    entity.setSourceSystem(requestContext.sourceSystem());
    entity.setExternalDocumentId(externalDocumentId);
    entity.setTitle(title);
    entity.setStorageKey(storageKey);
    entity.setFileType(fileType);
    entity.setDocumentType(documentType);
    entity.setStatus(STATUS_DRAFT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    insertEntity(entity);
    return entity;
  }

  private DocumentSaveStatusResponse toSaveStatus(DocumentMetadataEntity entity) {
    return new DocumentSaveStatusResponse(
        entity.getDocumentId(),
        entity.getStatus(),
        buildStatusMessage(entity),
        entity.getLastCallbackStatus(),
        entity.getLastCallbackAt(),
        entity.getLastSavedAt()
    );
  }

  private String buildStatusMessage(DocumentMetadataEntity entity) {
    return switch (entity.getStatus()) {
      case STATUS_EDITING -> entity.getLastCallbackAt() == null
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

  /**
   * 统一封装 insert，目的是把 ORM 框架差异压缩在一个地方。
   *
   * <p>后续如果需要补充审计字段、租户插件或数据权限逻辑，
   * 可以直接从这里下手，而不用到处散改 service 里的业务方法。
   */
  private void insertEntity(DocumentMetadataEntity entity) {
    documentMetadataMapper.insert(entity);
  }

  /**
   * 统一封装 updateById，保证所有状态流转都显式走主键更新。
   */
  private void updateEntity(DocumentMetadataEntity entity) {
    documentMetadataMapper.update(entity);
  }
}
