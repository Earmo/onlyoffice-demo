package com.earmo.onlyoffice.demo.web;

import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataEntity;
import com.earmo.onlyoffice.demo.service.DocumentMetadataService;
import com.earmo.onlyoffice.demo.service.DocumentStorageService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentApiController.class)
class DocumentApiControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private DocumentMetadataService documentMetadataService;

  @MockBean
  private DocumentStorageService documentStorageService;

  @MockBean
  private RequestContextResolver requestContextResolver;

  @Test
  void shouldListDocumentsForCurrentTenant() throws Exception {
    when(requestContextResolver.resolve(any())).thenReturn(new RequestContext("tenant-a", "native", "user-a", "Alice"));
    when(documentMetadataService.listDocuments("tenant-a")).thenReturn(List.of(entity("demo")));

    mockMvc.perform(get("/api/documents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[0].documentId").value("demo"))
        .andExpect(jsonPath("$.documents[0].tenantId").value("tenant-a"))
        .andExpect(jsonPath("$.documents[0].sourceSystem").value("native"));
  }

  @Test
  void shouldCreateDocumentExplicitly() throws Exception {
    when(requestContextResolver.resolve(any())).thenReturn(new RequestContext("tenant-a", "native", "user-a", "Alice"));
    when(documentStorageService.createNativeDocument(anyString(), anyString(), any(RequestContext.class), anyString()))
        .thenReturn(storedDocument("doc-1"));

    mockMvc.perform(post("/api/documents")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "documentId": "doc-1",
                  "title": "alpha.docx",
                  "externalDocumentId": "external-1"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value("doc-1"))
        .andExpect(jsonPath("$.title").value("alpha.docx"))
        .andExpect(jsonPath("$.ownerUserId").value("user-a"));
  }

  private DocumentMetadataEntity entity(String documentId) {
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId(documentId);
    entity.setTenantId("tenant-a");
    entity.setOwnerUserId("user-a");
    entity.setSourceSystem("native");
    entity.setTitle("demo.docx");
    entity.setStorageKey("documents/demo.docx");
    entity.setFileType("docx");
    entity.setDocumentType("word");
    entity.setStatus("draft");
    return entity;
  }

  private StoredDocument storedDocument(String documentId) {
    return new StoredDocument(
        documentId,
        "tenant-a",
        "user-a",
        "native",
        "external-1",
        "alpha.docx",
        "documents/" + documentId + ".docx",
        "docx",
        "word",
        "draft",
        java.nio.file.Path.of("documents/" + documentId + ".docx"),
        Instant.parse("2026-03-19T08:00:00Z"),
        null,
        null,
        null,
        null
    );
  }
}
