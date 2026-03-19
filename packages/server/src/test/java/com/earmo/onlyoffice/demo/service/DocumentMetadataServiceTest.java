package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataEntity;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentMetadataServiceTest {

  @Test
  void shouldPersistStateTransitionsThroughRepository() {
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
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

    when(repository.findById("demo")).thenReturn(Optional.of(entity));
    when(repository.save(any(DocumentMetadataEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    DocumentMetadataService service = new DocumentMetadataService(repository);

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
    DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
    when(repository.findById("doc-1")).thenReturn(Optional.empty());
    when(repository.save(any(DocumentMetadataEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    DocumentMetadataService service = new DocumentMetadataService(repository);
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
  }
}
