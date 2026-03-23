package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
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
import static org.mockito.Mockito.verify;
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

  @MockBean
  private DocumentMetadataMapper documentMetadataMapper;

  @Test
  void shouldListDocumentsForCurrentTenant() throws Exception {
    when(requestContextResolver.resolve(any())).thenReturn(new RequestContext("tenant-a", "native", "user-a", "Alice"));
    when(documentMetadataService.listDocuments("tenant-a")).thenReturn(List.of(entity("sample")));
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);

    mockMvc.perform(get("/api/documents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[0].documentId").value("sample"))
        .andExpect(jsonPath("$.documents[0].tenantId").value("tenant-a"))
        .andExpect(jsonPath("$.documents[0].sourceSystem").value("native"))
        .andExpect(jsonPath("$.documents[0].storageAvailable").value(true));
  }

  @Test
  void shouldExposeStorageAvailabilityForDocumentDetail() throws Exception {
    when(documentMetadataService.requireDocument("sample")).thenReturn(entity("sample"));
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(false);

    mockMvc.perform(get("/api/documents/sample"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value("sample"))
        .andExpect(jsonPath("$.storageAvailable").value(false));
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
        .andExpect(jsonPath("$.ownerUser").value("user-a"))
        .andExpect(jsonPath("$.storageAvailable").value(true));

    verify(documentStorageService).createNativeDocument(anyString(), anyString(), any(RequestContext.class), anyString());
  }

  private DocumentMetadataEntity entity(String documentId) {
    DocumentMetadataEntity entity = new DocumentMetadataEntity();
    entity.setDocumentId(documentId);
    entity.setTenantId("tenant-a");
    entity.setOwnerUser("user-a");
    entity.setSourceSystem("native");
    entity.setTitle("sample.docx");
    entity.setStorageKey("documents/sample.docx");
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
