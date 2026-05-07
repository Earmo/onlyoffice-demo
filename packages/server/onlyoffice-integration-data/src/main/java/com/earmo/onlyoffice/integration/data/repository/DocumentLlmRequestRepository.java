package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentLlmRequestEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmRequestMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentLlmRequestEntityTableDef.DOCUMENT_LLM_REQUEST_ENTITY;

@Repository
@RequiredArgsConstructor
public class DocumentLlmRequestRepository {

  private final DocumentLlmRequestMapper documentLlmRequestMapper;

  public void insert(DocumentLlmRequestEntity entity) {
    documentLlmRequestMapper.insert(entity);
  }

  public void update(DocumentLlmRequestEntity entity) {
    documentLlmRequestMapper.update(entity);
  }

  public Optional<DocumentLlmRequestEntity> findRequestByScope(
      String requestId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    return findRequestByScope(requestId, null, documentId, tenantId, actorUser);
  }

  public Optional<DocumentLlmRequestEntity> findRequestByScope(
      String requestId,
      String orgId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_REQUEST_ENTITY.REQUEST_ID.eq(requestId))
        .and(DOCUMENT_LLM_REQUEST_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_REQUEST_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_REQUEST_ENTITY.ACTOR_USER.eq(actorUser))
        .limit(1);
    if (orgId != null) {
      queryWrapper.and(DOCUMENT_LLM_REQUEST_ENTITY.ORG_ID.eq(orgId));
    }
    return Optional.ofNullable(documentLlmRequestMapper.selectOneByQuery(queryWrapper));
  }

  public Optional<DocumentLlmRequestEntity> findByRequestId(String requestId) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_REQUEST_ENTITY.REQUEST_ID.eq(requestId))
        .limit(1);
    return Optional.ofNullable(documentLlmRequestMapper.selectOneByQuery(queryWrapper));
  }

  public int markCancelRequested(
      String requestId,
      String tenantId,
      String actorUser,
      String cancelSource
  ) {
    return markCancelRequested(requestId, null, tenantId, actorUser, cancelSource);
  }

  public int markCancelRequested(
      String requestId,
      String orgId,
      String tenantId,
      String actorUser,
      String cancelSource
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_REQUEST_ENTITY.REQUEST_ID.eq(requestId))
        .and(DOCUMENT_LLM_REQUEST_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_REQUEST_ENTITY.ACTOR_USER.eq(actorUser));
    if (orgId != null) {
      queryWrapper.and(DOCUMENT_LLM_REQUEST_ENTITY.ORG_ID.eq(orgId));
    }
    queryWrapper.limit(1);
    Optional<DocumentLlmRequestEntity> entityOptional = Optional.ofNullable(documentLlmRequestMapper.selectOneByQuery(queryWrapper));
    if (entityOptional.isEmpty()) {
      return 0;
    }
    DocumentLlmRequestEntity entity = entityOptional.get();
    entity.setCancelRequested(true);
    entity.setCancelSource(cancelSource);
    documentLlmRequestMapper.update(entity);
    return 1;
  }
}
