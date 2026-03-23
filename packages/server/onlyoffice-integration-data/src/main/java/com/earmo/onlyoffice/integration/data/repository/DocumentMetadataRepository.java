package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_METADATA_ENTITY.TENANT_ID.eq(tenantId))
        .orderBy(DOCUMENT_METADATA_ENTITY.UPDATED_TIME.desc());
    return documentMetadataMapper.selectListByQuery(queryWrapper);
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
}
