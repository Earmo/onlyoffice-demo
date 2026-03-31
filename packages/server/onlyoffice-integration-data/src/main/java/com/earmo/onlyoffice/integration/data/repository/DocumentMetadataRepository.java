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

  private final DocumentMetadataMapper documentMetadataMapper;

  public List<DocumentMetadataEntity> listByTenant(String tenantId) {
    return listByTenant(tenantId, null, null, null, null, "desc");
  }

  public List<DocumentMetadataEntity> listByTenant(
      String tenantId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection
  ) {
    return documentMetadataMapper.selectListByQuery(
        buildTenantQuery(tenantId, query, status, sourceSystem, documentType, sortDirection)
    );
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
    return documentMetadataMapper.paginate(
        pageNumber,
        pageSize,
        buildTenantQuery(tenantId, query, status, sourceSystem, documentType, sortDirection)
    );
  }

  public Optional<DocumentMetadataEntity> findBySourceSystemAndExternalDocument(
      String sourceSystem,
      String externalDocumentId
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_METADATA_ENTITY.SOURCE_SYSTEM.eq(sourceSystem))
        .and(DOCUMENT_METADATA_ENTITY.EXTERNAL_DOCUMENT_ID.eq(externalDocumentId))
        .limit(1);
    return Optional.ofNullable(documentMetadataMapper.selectOneByQuery(queryWrapper));
  }

  private QueryWrapper buildTenantQuery(
      String tenantId,
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String sortDirection
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_METADATA_ENTITY.TENANT_ID.eq(tenantId));

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
      queryWrapper.orderBy(DOCUMENT_METADATA_ENTITY.UPDATED_TIME.desc(), DOCUMENT_METADATA_ENTITY.TITLE.desc());
    }
    return queryWrapper;
  }

  private boolean hasExplicitFilter(String value) {
    return StringUtils.hasText(value) && !"all".equalsIgnoreCase(value.trim());
  }
}
