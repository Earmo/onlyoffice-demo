package com.earmo.onlyoffice.demo.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DocumentMetadataRepositoryTest {

  @Autowired
  private DocumentMetadataRepository documentMetadataRepository;

  @Test
  void shouldPersistDocumentMetadata() {
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("doc-1");
    entity.setTenantId("tenant-a");
    entity.setOwnerUserId("user-a");
    entity.setSourceSystem("native");
    entity.setExternalDocumentId("external-1");
    entity.setTitle("alpha.docx");
    entity.setStorageKey("documents/doc-1.docx");
    entity.setFileType("docx");
    entity.setDocumentType("word");
    entity.setStatus("draft");

    documentMetadataRepository.save(entity);

    assertTrue(documentMetadataRepository.findById("doc-1").isPresent());
    assertEquals(
        "external-1",
        documentMetadataRepository.findBySourceSystemAndExternalDocumentId("native", "external-1")
            .orElseThrow()
            .getExternalDocumentId()
    );
  }
}
