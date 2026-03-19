package com.earmo.onlyoffice.demo.persistence;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DocumentMetadataMapperTest {

  @Autowired
  private DocumentMetadataMapper documentMetadataMapper;

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
    entity.setCreatedAt(Instant.parse("2026-03-19T08:00:00Z"));
    entity.setUpdatedAt(Instant.parse("2026-03-19T08:00:00Z"));

    documentMetadataMapper.insert(entity);

    assertTrue(documentMetadataMapper.selectOneById("doc-1") != null);
    assertEquals(
        "external-1",
        documentMetadataMapper.selectBySourceSystemAndExternalDocumentId("native", "external-1")
            .getExternalDocumentId()
    );
  }
}
