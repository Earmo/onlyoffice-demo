package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentLlmSessionEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmSessionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentLlmSessionEntityTableDef.DOCUMENT_LLM_SESSION_ENTITY;

@Repository
@RequiredArgsConstructor
public class DocumentLlmSessionRepository {

  private final DocumentLlmSessionMapper documentLlmSessionMapper;

  public void insert(DocumentLlmSessionEntity entity) {
    documentLlmSessionMapper.insert(entity);
  }

  public void update(DocumentLlmSessionEntity entity) {
    documentLlmSessionMapper.update(entity);
  }

  public Optional<DocumentLlmSessionEntity> findSessionByScope(
      String sessionId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_SESSION_ENTITY.SESSION_ID.eq(sessionId))
        .and(DOCUMENT_LLM_SESSION_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_SESSION_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_SESSION_ENTITY.ACTOR_USER.eq(actorUser))
        .and(DOCUMENT_LLM_SESSION_ENTITY.ARCHIVED_TIME.isNull())
        .limit(1);
    return Optional.ofNullable(documentLlmSessionMapper.selectOneByQuery(queryWrapper));
  }

  public Optional<DocumentLlmSessionEntity> findBySessionId(String sessionId) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_SESSION_ENTITY.SESSION_ID.eq(sessionId))
        .limit(1);
    return Optional.ofNullable(documentLlmSessionMapper.selectOneByQuery(queryWrapper));
  }

  public List<DocumentLlmSessionEntity> findSessionsByScope(
      String documentId,
      String tenantId,
      String actorUser,
      int limit
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_SESSION_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_SESSION_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_SESSION_ENTITY.ACTOR_USER.eq(actorUser))
        .and(DOCUMENT_LLM_SESSION_ENTITY.ARCHIVED_TIME.isNull())
        .orderBy(DOCUMENT_LLM_SESSION_ENTITY.UPDATED_TIME.desc())
        .limit(limit);
    return documentLlmSessionMapper.selectListByQuery(queryWrapper);
  }

  public void archiveOverflowSessions(
      String documentId,
      String tenantId,
      String actorUser,
      int keep,
      Instant archivedTime
  ) {
    List<DocumentLlmSessionEntity> sessions = findSessionsByScope(documentId, tenantId, actorUser, keep + 100);
    if (sessions.size() <= keep) {
      return;
    }
    for (int index = keep; index < sessions.size(); index++) {
      DocumentLlmSessionEntity entity = sessions.get(index);
      entity.setArchivedTime(archivedTime);
      entity.setUpdatedTime(archivedTime);
      update(entity);
    }
  }
}
