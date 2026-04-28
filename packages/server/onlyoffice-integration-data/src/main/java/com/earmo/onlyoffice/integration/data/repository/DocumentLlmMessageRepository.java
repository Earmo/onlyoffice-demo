package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmMessageMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentLlmMessageEntityTableDef.DOCUMENT_LLM_MESSAGE_ENTITY;

@Repository
@RequiredArgsConstructor
public class DocumentLlmMessageRepository {

  private final DocumentLlmMessageMapper documentLlmMessageMapper;

  public void insert(DocumentLlmMessageEntity entity) {
    documentLlmMessageMapper.insert(entity);
  }

  public void update(DocumentLlmMessageEntity entity) {
    documentLlmMessageMapper.update(entity);
  }

  public int updateActiveVariantIndex(
      String messageId,
      String documentId,
      String tenantId,
      String actorUser,
      int activeVariantIndex
  ) {
    Optional<DocumentLlmMessageEntity> entityOptional = findMessageByScope(messageId, documentId, tenantId, actorUser);
    if (entityOptional.isEmpty()) {
      return 0;
    }
    DocumentLlmMessageEntity entity = entityOptional.get();
    entity.setActiveVariantIndex(activeVariantIndex);
    documentLlmMessageMapper.update(entity);
    return 1;
  }

  public Optional<DocumentLlmMessageEntity> findMessageByScope(
      String messageId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_MESSAGE_ENTITY.MESSAGE_ID.eq(messageId))
        .and(DOCUMENT_LLM_MESSAGE_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_MESSAGE_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_MESSAGE_ENTITY.ACTOR_USER.eq(actorUser))
        .limit(1);
    return Optional.ofNullable(documentLlmMessageMapper.selectOneByQuery(queryWrapper));
  }

  public List<DocumentLlmMessageEntity> findMessagesBySessionScope(
      String sessionId,
      String documentId,
      String tenantId,
      String actorUser,
      int limit
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_MESSAGE_ENTITY.SESSION_ID.eq(sessionId))
        .and(DOCUMENT_LLM_MESSAGE_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_MESSAGE_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_MESSAGE_ENTITY.ACTOR_USER.eq(actorUser))
        .orderBy(DOCUMENT_LLM_MESSAGE_ENTITY.CREATED_TIME.asc())
        .limit(limit);
    return documentLlmMessageMapper.selectListByQuery(queryWrapper);
  }
}
