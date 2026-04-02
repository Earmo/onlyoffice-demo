package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentEditorSessionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentEditorSessionEntityTableDef.DOCUMENT_EDITOR_SESSION_ENTITY;

/**
 * 文档编辑会话 repository。
 *
 * <p>这里专门承接“活跃编辑会话”查询入口：
 * service 层不再自己拼 `closed_time is null` 之类的细节，
 * 而是直接表达“查当前活跃会话”“关闭当前会话”“统计文档活跃编辑人数”。
 */
@Repository
@RequiredArgsConstructor
public class DocumentEditorSessionRepository {

  private final DocumentEditorSessionMapper documentEditorSessionMapper;

  public Optional<DocumentEditorSessionEntity> findActiveByDocumentIdAndActorUser(
      String documentId,
      String actorUser
  ) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_EDITOR_SESSION_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_EDITOR_SESSION_ENTITY.ACTOR_USER.eq(actorUser))
        .and(DOCUMENT_EDITOR_SESSION_ENTITY.CLOSED_TIME.isNull())
        .limit(1);
    return Optional.ofNullable(documentEditorSessionMapper.selectOneByQuery(queryWrapper));
  }

  public void insert(DocumentEditorSessionEntity entity) {
    documentEditorSessionMapper.insert(entity);
  }

  public void update(DocumentEditorSessionEntity entity) {
    documentEditorSessionMapper.update(entity);
  }

  public long countActiveByDocumentId(String documentId, Instant activeSince) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_EDITOR_SESSION_ENTITY.DOCUMENT_ID.eq(documentId))
        .and(DOCUMENT_EDITOR_SESSION_ENTITY.CLOSED_TIME.isNull())
        .and(DOCUMENT_EDITOR_SESSION_ENTITY.LAST_SEEN_TIME.ge(activeSince));
    return documentEditorSessionMapper.selectCountByQuery(queryWrapper);
  }

  public Map<String, Integer> countActiveByDocumentIds(List<String> documentIds, Instant activeSince) {
    if (documentIds == null || documentIds.isEmpty()) {
      return Map.of();
    }

    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_EDITOR_SESSION_ENTITY.DOCUMENT_ID.in(documentIds))
        .and(DOCUMENT_EDITOR_SESSION_ENTITY.CLOSED_TIME.isNull())
        .and(DOCUMENT_EDITOR_SESSION_ENTITY.LAST_SEEN_TIME.ge(activeSince));

    Map<String, Integer> counts = new LinkedHashMap<>();
    for (DocumentEditorSessionEntity entity : documentEditorSessionMapper.selectListByQuery(queryWrapper)) {
      counts.merge(entity.getDocumentId(), 1, Integer::sum);
    }
    return counts;
  }
}
