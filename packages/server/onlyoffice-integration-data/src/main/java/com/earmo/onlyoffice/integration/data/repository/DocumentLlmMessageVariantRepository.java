package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageVariantEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmMessageVariantMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentLlmMessageVariantEntityTableDef.DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY;

@Repository
@RequiredArgsConstructor
public class DocumentLlmMessageVariantRepository {

  private final DocumentLlmMessageVariantMapper documentLlmMessageVariantMapper;
  private final DocumentLlmMessageRepository documentLlmMessageRepository;

  public void insert(DocumentLlmMessageVariantEntity entity) {
    documentLlmMessageVariantMapper.insert(entity);
  }

  public void update(DocumentLlmMessageVariantEntity entity) {
    documentLlmMessageVariantMapper.update(entity);
  }

  public Optional<DocumentLlmMessageVariantEntity> findVariantByMessageScope(
      String messageId,
      String variantId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    return findVariantByMessageScope(messageId, variantId, null, documentId, tenantId, actorUser);
  }

  public Optional<DocumentLlmMessageVariantEntity> findVariantByMessageScope(
      String messageId,
      String variantId,
      String orgId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.MESSAGE_ID.eq(messageId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.VARIANT_ID.eq(variantId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ACTOR_USER.eq(actorUser))
        .limit(1);
    if (orgId != null) {
      queryWrapper.and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ORG_ID.eq(orgId));
    }
    return Optional.ofNullable(documentLlmMessageVariantMapper.selectOneByQuery(queryWrapper));
  }

  public List<DocumentLlmMessageVariantEntity> findByMessageScope(
      String messageId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    return findByMessageScope(messageId, null, documentId, tenantId, actorUser);
  }

  public List<DocumentLlmMessageVariantEntity> findByMessageScope(
      String messageId,
      String orgId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.MESSAGE_ID.eq(messageId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ACTOR_USER.eq(actorUser))
        .orderBy(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.VARIANT_INDEX.asc());
    if (orgId != null) {
      queryWrapper.and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ORG_ID.eq(orgId));
    }
    return documentLlmMessageVariantMapper.selectListByQuery(queryWrapper);
  }

  public List<DocumentLlmMessageVariantEntity> findByMessageIdsScope(
      Collection<String> messageIds,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    return findByMessageIdsScope(messageIds, null, documentId, tenantId, actorUser);
  }

  public List<DocumentLlmMessageVariantEntity> findByMessageIdsScope(
      Collection<String> messageIds,
      String orgId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    if (messageIds == null || messageIds.isEmpty()) {
      return List.of();
    }
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.MESSAGE_ID.in(messageIds))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ACTOR_USER.eq(actorUser))
        .orderBy(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.MESSAGE_ID.asc())
        .orderBy(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.VARIANT_INDEX.asc());
    if (orgId != null) {
      queryWrapper.and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ORG_ID.eq(orgId));
    }
    return documentLlmMessageVariantMapper.selectListByQuery(queryWrapper);
  }

  @Transactional
  public DocumentLlmMessageVariantEntity createNextVariantForMessageScope(
      String messageId,
      String documentId,
      String tenantId,
      String actorUser,
      String status,
      Instant now
  ) {
    return createNextVariantForMessageScope(messageId, null, documentId, tenantId, actorUser, status, now);
  }

  @Transactional
  public DocumentLlmMessageVariantEntity createNextVariantForMessageScope(
      String messageId,
      String orgId,
      String documentId,
      String tenantId,
      String actorUser,
      String status,
      Instant now
  ) {
    DocumentLlmMessageEntity message = documentLlmMessageRepository.findMessageByScope(
            messageId,
            orgId,
            documentId,
            tenantId,
            actorUser
        )
        .orElseThrow(() -> new IllegalArgumentException("assistant message 不存在或无权访问。"));
    int attempts = 0;
    while (attempts < 2) {
      attempts++;
      int nextVariantIndex = nextVariantIndex(messageId, orgId, documentId, tenantId, actorUser);
      DocumentLlmMessageVariantEntity entity = new DocumentLlmMessageVariantEntity();
      entity.setVariantId(UUID.randomUUID().toString());
      entity.setMessageId(message.getMessageId());
      entity.setSessionId(message.getSessionId());
      entity.setDocumentId(message.getDocumentId());
      entity.setTenantId(message.getTenantId());
      entity.setOrgId(message.getOrgId());
      entity.setOrgName(message.getOrgName());
      entity.setActorUser(message.getActorUser());
      entity.setVariantIndex(nextVariantIndex);
      entity.setStatus(status);
      entity.setCreatedTime(now);
      entity.setUpdatedTime(now);
      try {
        documentLlmMessageVariantMapper.insert(entity);
        return entity;
      } catch (RuntimeException ex) {
        if (!isUniqueVariantIndexConflict(ex) || attempts >= 2) {
          throw ex;
        }
      }
    }
    throw new IllegalStateException("无法创建新的 LLM message variant。");
  }

  private int nextVariantIndex(String messageId, String documentId, String tenantId, String actorUser) {
    return nextVariantIndex(messageId, null, documentId, tenantId, actorUser);
  }

  private int nextVariantIndex(String messageId, String orgId, String documentId, String tenantId, String actorUser) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .select(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.VARIANT_INDEX)
        .where(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.MESSAGE_ID.eq(messageId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.TENANT_ID.eq(tenantId))
        .and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ACTOR_USER.eq(actorUser))
        .orderBy(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.VARIANT_INDEX.desc())
        .limit(1);
    if (orgId != null) {
      queryWrapper.and(DOCUMENT_LLM_MESSAGE_VARIANT_ENTITY.ORG_ID.eq(orgId));
    }
    DocumentLlmMessageVariantEntity latest = documentLlmMessageVariantMapper.selectOneByQuery(queryWrapper);
    return latest == null || latest.getVariantIndex() == null ? 0 : latest.getVariantIndex() + 1;
  }

  private boolean isUniqueVariantIndexConflict(RuntimeException ex) {
    Throwable current = ex;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.contains("UK_DOCUMENT_LLM_MESSAGE_VARIANT_INDEX")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
