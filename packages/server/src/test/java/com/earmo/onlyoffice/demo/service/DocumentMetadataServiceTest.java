package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataEntity;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentMetadataServiceTest {

  @Test
  void shouldPersistStateTransitionsThroughMapper() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("demo");
    entity.setTenantId("tenant-a");
    entity.setOwnerUserId("user-a");
    entity.setSourceSystem("native");
    entity.setTitle("demo.docx");
    entity.setStorageKey("documents/demo.docx");
    entity.setFileType("docx");
    entity.setDocumentType("word");
    entity.setStatus("draft");

    when(mapper.selectOneById("demo")).thenReturn(entity);
    when(mapper.update(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataService(mapper);

    DocumentSaveStatusResponse callback = service.recordCallbackReceived("demo", 2);
    DocumentSaveStatusResponse saved = service.markSaved("demo", 2);
    DocumentSaveStatusResponse failed = service.markFailed("demo", 6, "下载失败");

    assertEquals("editing", callback.state());
    assertEquals("saved", saved.state());
    assertEquals("failed", failed.state());
    assertEquals("回写共享存储失败：下载失败", failed.message());
  }

  @Test
  void shouldCreateDocumentWithSharedMetadataFields() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    when(mapper.selectOneById("doc-1")).thenReturn(null);
    when(mapper.selectBySourceSystemAndExternalDocumentId(eq("native"), eq("external-1"))).thenReturn(null);
    when(mapper.insert(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataService(mapper);
    DocumentMetadataEntity entity = service.createDocument(
        "doc-1",
        "alpha.docx",
        "docx",
        "word",
        "documents/doc-1.docx",
        new RequestContext("tenant-a", "native", "user-a", "Alice"),
        "external-1"
    );

    assertEquals("tenant-a", entity.getTenantId());
    assertEquals("user-a", entity.getOwnerUserId());
    assertEquals("native", entity.getSourceSystem());
    assertEquals("external-1", entity.getExternalDocumentId());
    assertEquals("draft", entity.getStatus());
    assertEquals("doc-1", entity.getDocumentId());
  }
}
