package com.earmo.onlyoffice.integration.data.entity;

import com.earmo.onlyoffice.integration.data.DataModuleTestApplication;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = DataModuleTestApplication.class)
class DocumentMetadataMapperTest {

  @Autowired
  private DocumentMetadataMapper documentMetadataMapper;

  @Test
  void shouldPersistDocumentMetadata() {
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId("doc-1");
    entity.setTenantId("tenant-a");
    entity.setOwnerUser("user-a");
    entity.setSourceSystem("native");
    entity.setExternalDocumentId("external-1");
    entity.setTitle("alpha.docx");
    entity.setStorageKey("documents/doc-1.docx");
    entity.setFileType("docx");
    entity.setDocumentType("word");
    entity.setStatus("draft");
    entity.setCreatedTime(Instant.parse("2026-03-19T08:00:00Z"));
    entity.setUpdatedTime(Instant.parse("2026-03-19T08:00:00Z"));

    documentMetadataMapper.insert(entity);

    DocumentMetadataEntity stored = documentMetadataMapper.selectOneById("doc-1");
    assertNotNull(stored);
    assertEquals("user-a", stored.getOwnerUser());
    assertEquals(Instant.parse("2026-03-19T08:00:00Z"), stored.getCreatedTime());
    assertEquals(Instant.parse("2026-03-19T08:00:00Z"), stored.getUpdatedTime());
  }
}
