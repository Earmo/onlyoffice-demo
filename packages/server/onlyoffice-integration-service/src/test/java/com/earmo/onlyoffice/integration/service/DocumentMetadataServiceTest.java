package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.data.repository.DocumentMetadataRepository;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.service.impl.DocumentMetadataServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentMetadataServiceTest {

  @Test
  void shouldPersistStateTransitionsThroughMapper() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("sample");
    entity.setTenantId("tenant-a");
    entity.setOwnerUser("user-a");
    entity.setSourceSystem("native");
    entity.setTitle("sample.docx");
    entity.setStorageKey("documents/sample.docx");
    entity.setFileType("docx");
    entity.setDocumentType("word");
    entity.setStatus("draft");

    when(mapper.selectOneById("sample")).thenReturn(entity);
    when(mapper.update(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataServiceImpl(mapper, repository);

    DocumentSaveStatusResponse callback = service.recordCallbackReceived("sample", 2);
    DocumentSaveStatusResponse saved = service.markSaved("sample", 2);
    DocumentSaveStatusResponse failed = service.markFailed("sample", 6, "下载失败");

    assertEquals("editing", callback.state());
    assertEquals("saved", saved.state());
    assertEquals("failed", failed.state());
    assertEquals("回写共享存储失败：下载失败", failed.message());
    assertEquals(0, failed.recentEvents().size());
  }

  @Test
  void shouldUpdateDocumentFormatFieldsWhenActualContentWasNormalized() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("sample");
    entity.setTenantId("tenant-a");
    entity.setOwnerUser("user-a");
    entity.setSourceSystem("native");
    entity.setTitle("sample.doc");
    entity.setStorageKey("documents/sample.doc");
    entity.setFileType("doc");
    entity.setDocumentType("word");
    entity.setStatus("draft");

    when(mapper.selectOneById("sample")).thenReturn(entity);
    when(mapper.update(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataServiceImpl(mapper, repository);
    DocumentMetadataEntity updated = service.updateDocumentFormat("sample", "sample.docx", "docx", "word");

    assertEquals("sample.docx", updated.getTitle());
    assertEquals("docx", updated.getFileType());
    assertEquals("word", updated.getDocumentType());
    assertNotNull(updated.getUpdatedTime());
  }

  @Test
  void shouldCreateDocumentWithSharedMetadataFields() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    when(mapper.selectOneById("doc-1")).thenReturn(null);
    when(repository.findBySourceSystemAndExternalDocument(eq("native"), eq("external-1")))
        .thenReturn(Optional.empty());
    when(mapper.insert(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataServiceImpl(mapper, repository);
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
    assertEquals("user-a", entity.getOwnerUser());
    assertEquals("native", entity.getSourceSystem());
    assertEquals("external-1", entity.getExternalDocumentId());
    assertEquals("draft", entity.getStatus());
    assertEquals("doc-1", entity.getDocumentId());
  }

  @Test
  void shouldArchiveDocumentByUpdatingStatusAndTimestamp() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("doc-archived");
    entity.setStatus("saved");
    when(mapper.selectOneById("doc-archived")).thenReturn(entity);
    when(mapper.update(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataServiceImpl(mapper, repository);
    DocumentMetadataEntity archived = service.archiveDocument("doc-archived");

    assertEquals("archived", archived.getStatus());
    assertNotNull(archived.getUpdatedTime());
  }

  @Test
  void shouldCreateNewDocumentWhenExternalMappingWasArchived() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    DocumentMetadataEntity archived = new DocumentMetadataEntity();
    archived.setDocumentId("doc-old");
    archived.setStatus("archived");

    when(repository.findBySourceSystemAndExternalDocument(eq("native"), eq("external-1")))
        .thenReturn(Optional.of(archived));
    when(mapper.selectOneById("doc-new")).thenReturn(null);
    when(mapper.insert(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataServiceImpl(mapper, repository);
    DocumentMetadataEntity entity = service.createDocument(
        "doc-new",
        "alpha.docx",
        "docx",
        "word",
        "documents/doc-new.docx",
        new RequestContext("tenant-a", "native", "user-a", "Alice"),
        "external-1"
    );

    assertEquals("doc-new", entity.getDocumentId());
    assertEquals("draft", entity.getStatus());
  }

  @Test
  void shouldKeepEditingBatchOpenTimeStableWhenDocumentAlreadyEditing() {
    DocumentMetadataMapper mapper = mock(DocumentMetadataMapper.class);
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("sample");
    entity.setStatus("editing");
    entity.setLastOpenedTime(java.time.Instant.parse("2026-03-25T09:59:00Z"));

    when(mapper.selectOneById("sample")).thenReturn(entity);
    when(mapper.update(any(DocumentMetadataEntity.class))).thenReturn(1);

    DocumentMetadataService service = new DocumentMetadataServiceImpl(mapper, repository);
    DocumentSaveStatusResponse status = service.markEditingStarted("sample");

    assertEquals("editing", status.state());
    assertEquals(java.time.Instant.parse("2026-03-25T09:59:00Z"), entity.getLastOpenedTime());
    assertNotNull(entity.getUpdatedTime());
  }
}
