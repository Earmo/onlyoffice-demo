package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.AccessAuditEventEntity;
import com.earmo.onlyoffice.integration.data.mapper.AccessAuditEventMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.earmo.onlyoffice.integration.data.entity.table.AccessAuditEventEntityTableDef.ACCESS_AUDIT_EVENT_ENTITY;

/**
 * 访问审计 repository。
 */
@Repository
@RequiredArgsConstructor
public class AccessAuditEventRepository {

  private final AccessAuditEventMapper accessAuditEventMapper;

  public void save(AccessAuditEventEntity entity) {
    accessAuditEventMapper.insert(entity);
  }

  public List<AccessAuditEventEntity> listByDocumentId(String documentId) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(ACCESS_AUDIT_EVENT_ENTITY.DOCUMENT_ID.eq(documentId))
        .orderBy(ACCESS_AUDIT_EVENT_ENTITY.EVENT_TIME.desc());
    return accessAuditEventMapper.selectListByQuery(queryWrapper);
  }

  public List<AccessAuditEventEntity> listByTenantId(String tenantId) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(ACCESS_AUDIT_EVENT_ENTITY.TENANT_ID.eq(tenantId))
        .orderBy(ACCESS_AUDIT_EVENT_ENTITY.EVENT_TIME.desc());
    return accessAuditEventMapper.selectListByQuery(queryWrapper);
  }
}
