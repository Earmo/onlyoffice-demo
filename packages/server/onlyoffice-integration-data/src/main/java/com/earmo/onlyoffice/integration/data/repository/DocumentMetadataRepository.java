package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentMetadataEntityTableDef.DOCUMENT_METADATA_ENTITY;

/**
 * 文档元数据 repository。
 *
 * <p>这个类的职责不是替代 BaseMapper，而是承接“有领域语义的查询入口”：
 * service 层不再自己拼 SQL，也不再依赖 mapper 注解查询，而是统一通过 repository 表达查询意图。
 */
@Repository
@RequiredArgsConstructor
public class DocumentMetadataRepository {

  private static final String STATUS_ARCHIVED = "archived";

  private final DocumentMetadataMapper documentMetadataMapper;

  public List<DocumentMetadataEntity> listByTenant(String tenantId) {
    return listVisibleByTenant(tenantId, null, null, null, null, null, "desc");
  }

  public List<DocumentMetadataEntity> listByTenant(String tenantId, String orgId) {
    return listVisibleByTenant(tenantId, orgId, null, null, null, null, "desc");
  }

  public List<DocumentMetadataEntity> listByTenant(
      String tenantId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection
  ) {
    return listVisibleByTenant(tenantId, null, query, status, sourceSystem, documentType, sortDirection);
  }

  public List<DocumentMetadataEntity> listVisibleByTenant(
      String tenantId,
      String orgId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection
  ) {
    return documentMetadataMapper.selectListByQuery(
        buildTenantQuery(tenantId, orgId, query, status, sourceSystem, documentType, sortDirection, true, null)
    );
  }

  public List<DocumentMetadataEntity> listVisibleByTenant(
      String tenantId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection
  ) {
    return listVisibleByTenant(tenantId, null, query, status, sourceSystem, documentType, sortDirection);
  }

  public Page<DocumentMetadataEntity> paginateByTenant(
      String tenantId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection,
      int pageNumber,
      int pageSize
  ) {
    return paginateVisibleByTenant(tenantId, null, query, status, sourceSystem, documentType, sortDirection, pageNumber, pageSize);
  }

  public Page<DocumentMetadataEntity> paginateByTenant(
      String tenantId,
      String orgId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection,
      int pageNumber,
      int pageSize
  ) {
    return paginateVisibleByTenant(tenantId, orgId, query, status, sourceSystem, documentType, sortDirection, pageNumber, pageSize);
  }

  public Page<DocumentMetadataEntity> paginateVisibleByTenant(
      String tenantId,
      String orgId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection,
      int pageNumber,
      int pageSize
  ) {
    return documentMetadataMapper.paginate(
        pageNumber,
        pageSize,
        buildTenantQuery(tenantId, orgId, query, status, sourceSystem, documentType, sortDirection, true, null)
    );
  }

  public Page<DocumentMetadataEntity> paginateVisibleByTenant(
      String tenantId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection,
      int pageNumber,
      int pageSize
  ) {
    return paginateVisibleByTenant(tenantId, null, query, status, sourceSystem, documentType, sortDirection, pageNumber, pageSize);
  }

  public Optional<DocumentMetadataEntity> findBySourceSystemAndExternalDocument(
      String sourceSystem,
      String externalDocumentId
  ) {
    return findBySourceSystemAndExternalDocument(null, null, sourceSystem, externalDocumentId);
  }

  public Optional<DocumentMetadataEntity> findBySourceSystemAndExternalDocument(
      String tenantId,
      String orgId,
      String sourceSystem,
      String externalDocumentId
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_METADATA_ENTITY.SOURCE_SYSTEM.eq(sourceSystem))
        .and(DOCUMENT_METADATA_ENTITY.EXTERNAL_DOCUMENT_ID.eq(externalDocumentId));
    if (StringUtils.hasText(tenantId)) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.TENANT_ID.eq(tenantId));
    }
    if (StringUtils.hasText(orgId)) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.ORG_ID.eq(orgId.trim()));
    }
    queryWrapper.limit(1);
    return Optional.ofNullable(documentMetadataMapper.selectOneByQuery(queryWrapper));
  }

  public List<DocumentMetadataEntity> listRecentVisibleByTenant(String tenantId, int limit) {
    return listRecentVisibleByTenant(tenantId, null, limit);
  }

  public List<DocumentMetadataEntity> listRecentVisibleByTenant(String tenantId, String orgId, int limit) {
    return documentMetadataMapper.selectListByQuery(
        buildTenantQuery(tenantId, orgId, null, null, null, null, "desc", true, limit)
    );
  }

  private QueryWrapper buildTenantQuery(
      String tenantId,
      String orgId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection,
      boolean visibleOnly,
      Integer limit
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_METADATA_ENTITY.TENANT_ID.eq(tenantId));
    if (StringUtils.hasText(orgId)) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.ORG_ID.eq(orgId.trim()));
    }

    if (visibleOnly) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.STATUS.ne(STATUS_ARCHIVED));
    }
    if (StringUtils.hasText(query)) {
      String normalizedQuery = query.trim();
      queryWrapper.and(
          DOCUMENT_METADATA_ENTITY.TITLE.like(normalizedQuery)
              .or(DOCUMENT_METADATA_ENTITY.DOCUMENT_ID.like(normalizedQuery))
              .or(DOCUMENT_METADATA_ENTITY.EXTERNAL_DOCUMENT_ID.like(normalizedQuery))
      );
    }
    if (hasExplicitFilter(status)) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.STATUS.eq(status.trim()));
    }
    if (hasExplicitFilter(sourceSystem)) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.SOURCE_SYSTEM.eq(sourceSystem.trim()));
    }
    if (hasExplicitFilter(documentType)) {
      queryWrapper.and(DOCUMENT_METADATA_ENTITY.DOCUMENT_TYPE.eq(documentType.trim()));
    }

    if ("asc".equalsIgnoreCase(sortDirection)) {
      queryWrapper.orderBy(DOCUMENT_METADATA_ENTITY.UPDATED_TIME.asc(), DOCUMENT_METADATA_ENTITY.TITLE.asc());
    } else {
      queryWrapper.orderBy(DOCUMENT_METADATA_ENTITY.UPDATED_TIME.desc(), DOCUMENT_METADATA_ENTITY.TITLE.asc());
    }
    if (limit != null && limit > 0) {
      queryWrapper.limit(limit);
    }
    return queryWrapper;
  }

  private boolean hasExplicitFilter(String value) {
    return StringUtils.hasText(value) && !"all".equalsIgnoreCase(value.trim());
  }
}
