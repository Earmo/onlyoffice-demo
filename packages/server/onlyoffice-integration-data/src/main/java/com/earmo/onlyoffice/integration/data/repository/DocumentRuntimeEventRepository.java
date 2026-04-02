package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentRuntimeEventMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.earmo.onlyoffice.integration.data.entity.table.DocumentRuntimeEventEntityTableDef.DOCUMENT_RUNTIME_EVENT_ENTITY;

/**
 * 文档运行事件 repository。
 *
 * <p>这个类负责承接运行态关键轨迹的业务查询入口，
 * 避免 service 层自己拼事件流 SQL。
 */
@Repository
@RequiredArgsConstructor
public class DocumentRuntimeEventRepository {

  private final DocumentRuntimeEventMapper documentRuntimeEventMapper;

  public void save(DocumentRuntimeEventEntity entity) {
    documentRuntimeEventMapper.insert(entity);
  }

  public List<DocumentRuntimeEventEntity> listRecentByDocumentId(String documentId, long limit) {
    QueryWrapper queryWrapper = QueryWrapper.create()
        .where(DOCUMENT_RUNTIME_EVENT_ENTITY.DOCUMENT_ID.eq(documentId))
        .orderBy(DOCUMENT_RUNTIME_EVENT_ENTITY.EVENT_TIME.desc())
        .limit(limit);
    return documentRuntimeEventMapper.selectListByQuery(queryWrapper);
  }
}
