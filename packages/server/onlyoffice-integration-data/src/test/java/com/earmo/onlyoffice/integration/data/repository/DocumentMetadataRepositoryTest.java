package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.DataModuleTestApplication;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = DataModuleTestApplication.class)
class DocumentMetadataRepositoryTest {

  @Autowired
  private DocumentMetadataMapper documentMetadataMapper;

  @Autowired
  private DocumentMetadataRepository documentMetadataRepository;

  @Test
  void shouldListByTenantOrderByUpdatedTimeDesc() {
    documentMetadataMapper.insert(entity("repo-1", "tenant-repo-a", "external-1", Instant.parse("2026-03-19T08:00:00Z")));
    documentMetadataMapper.insert(entity("repo-2", "tenant-repo-a", "external-2", Instant.parse("2026-03-19T09:00:00Z")));

    List<DocumentMetadataEntity> documents = documentMetadataRepository.listByTenant("tenant-repo-a");

    assertEquals(2, documents.size());
    assertEquals("repo-2", documents.get(0).getDocumentId());
    assertEquals("repo-1", documents.get(1).getDocumentId());
  }

  @Test
  void shouldFindBySourceSystemAndExternalDocument() {
    documentMetadataMapper.insert(entity("repo-3", "tenant-b", "external-9", Instant.parse("2026-03-19T10:00:00Z")));

    Optional<DocumentMetadataEntity> document = documentMetadataRepository.findBySourceSystemAndExternalDocument(
        "native",
        "external-9"
    );

    assertTrue(document.isPresent());
    assertEquals("repo-3", document.get().getDocumentId());
  }

  private DocumentMetadataEntity entity(
      String documentId,
      String tenantId,
      String externalDocumentId,
      Instant updatedTime
  ) {
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId(documentId);
    entity.setTenantId(tenantId);
    entity.setOwnerUser("user-a");
    entity.setSourceSystem("native");
    entity.setExternalDocumentId(externalDocumentId);
    entity.setTitle(documentId + ".docx");
    entity.setStorageKey("documents/" + documentId + ".docx");
    entity.setFileType("docx");
    entity.setDocumentType("word");
    entity.setStatus("draft");
    entity.setCreatedTime(updatedTime.minusSeconds(60));
    entity.setUpdatedTime(updatedTime);
    return entity;
  }
}
