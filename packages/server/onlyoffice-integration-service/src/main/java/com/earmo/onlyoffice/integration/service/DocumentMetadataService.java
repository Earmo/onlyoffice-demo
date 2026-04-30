package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.mybatisflex.core.paginate.Page;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档主数据服务契约。
 *
 * <p>这个接口表达的是“文档元数据真相源”层能力：
 * 文档是否存在、属于谁、当前摘要状态是什么，以及如何把持久化实体投影回业务模型。
 */
public interface DocumentMetadataService {

    String STATUS_DRAFT = "draft";
    String STATUS_EDITING = "editing";
    String STATUS_SAVED = "saved";
    String STATUS_FAILED = "failed";
    String STATUS_ARCHIVED = "archived";

    Optional<DocumentMetadataEntity> findDocument(String documentId);

    DocumentMetadataEntity requireDocument(String documentId);

    DocumentMetadataEntity requireAccessibleDocument(String documentId);

    List<DocumentMetadataEntity> listDocuments(String tenantId);

    List<DocumentMetadataEntity> listDocuments(
            String tenantId,
            String query,
            String status,
            String sourceSystem,
            String documentType,
            String sortDirection
    );

    Page<DocumentMetadataEntity> listDocumentPage(
            String tenantId,
            String query,
            String status,
            String sourceSystem,
            String documentType,
            String sortDirection,
            int pageNumber,
            int pageSize
    );

    List<DocumentMetadataEntity> listRecentDocuments(String tenantId, int limit);

    DocumentMetadataEntity createDocument(
            String documentId,
            String title,
            String fileType,
            String documentType,
            String storageKey,
            RequestContext requestContext,
            String externalDocumentId
    );

    DocumentMetadataEntity createDocument(
            String documentId,
            String title,
            String fileType,
            String documentType,
            String storageKey,
            RequestContext requestContext,
            String ownerUser,
            String externalDocumentId
    );

    DocumentMetadataEntity archiveDocument(String documentId);

    DocumentSaveStatusResponse markOpened(String documentId);

    DocumentSaveStatusResponse markEditingStarted(String documentId);

    DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus);

    DocumentSaveStatusResponse markSaved(String documentId, Integer callbackStatus);

    DocumentMetadataEntity updateDocumentFormat(String documentId, String title, String fileType, String documentType);

    DocumentSaveStatusResponse markFailed(String documentId, Integer callbackStatus, String message);

    DocumentSaveStatusResponse reconcileClosedEditingSession(String documentId);

    DocumentSaveStatusResponse getStatus(String documentId);

    StoredDocument toStoredDocument(DocumentMetadataEntity entity, Path path, Instant lastModified);
}
