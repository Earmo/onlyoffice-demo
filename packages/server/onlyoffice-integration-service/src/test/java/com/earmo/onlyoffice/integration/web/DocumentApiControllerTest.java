package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.AccessAuditEventMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentEditorSessionMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentRuntimeEventMapper;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
  private DocumentStatusService documentStatusService;

  @MockBean
  private AccessAuditService accessAuditService;

  @MockBean
  private AccessContextResolver accessContextResolver;

  @MockBean
  private DocumentMetadataMapper documentMetadataMapper;

  @MockBean
  private AccessAuditEventMapper accessAuditEventMapper;

  @MockBean
  private DocumentRuntimeEventMapper documentRuntimeEventMapper;

  @MockBean
  private DocumentEditorSessionMapper documentEditorSessionMapper;

  @Test
  void shouldListDocumentsForCurrentTenant() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentMetadataService.listDocuments("tenant-a", null, null, null, null, "desc"))
        .thenReturn(List.of(entity("sample")));
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
    when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

    mockMvc.perform(get("/api/documents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value("tenant-a"))
        .andExpect(jsonPath("$.actorUser").value("user-a"))
        .andExpect(jsonPath("$.actorName").value("Alice"))
        .andExpect(jsonPath("$.documents[0].documentId").value("sample"))
        .andExpect(jsonPath("$.documents[0].tenantId").value("tenant-a"))
        .andExpect(jsonPath("$.documents[0].actorUser").value("user-a"))
        .andExpect(jsonPath("$.documents[0].actorName").value("Alice"))
        .andExpect(jsonPath("$.documents[0].sourceSystem").value("native"))
        .andExpect(jsonPath("$.documents[0].storageAvailable").value(true));

    verify(documentMetadataService).listDocuments("tenant-a", null, null, null, null, "desc");
  }

  @Test
  void shouldForwardQueryAndFilterParameters() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentMetadataService.listDocuments("tenant-a", "roadmap", "failed", "native", "word", "asc"))
        .thenReturn(List.of(entity("sample")));
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
    when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

    mockMvc.perform(
            get("/api/documents")
                .param("query", "roadmap")
                .param("status", "failed")
                .param("sourceSystem", "native")
                .param("documentType", "word")
                .param("sortDirection", "asc")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[0].documentId").value("sample"));

    verify(documentMetadataService).listDocuments("tenant-a", "roadmap", "failed", "native", "word", "asc");
  }

  @Test
  void shouldFilterByStorageAvailabilityProjection() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentMetadataService.listDocuments("tenant-a", null, null, null, null, "desc"))
        .thenReturn(List.of(entity("available"), entity("missing")));
    when(documentStorageService.exists(argThat(entity -> entity != null && "available".equals(entity.getDocumentId()))))
        .thenReturn(true);
    when(documentStorageService.exists(argThat(entity -> entity != null && "missing".equals(entity.getDocumentId()))))
        .thenReturn(false);
    when(documentStatusService.countActiveEditingSessions(List.of("available", "missing")))
        .thenReturn(java.util.Map.of("available", 0, "missing", 0));

    mockMvc.perform(get("/api/documents").param("storage", "unavailable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.length()").value(1))
        .andExpect(jsonPath("$.documents[0].documentId").value("missing"))
        .andExpect(jsonPath("$.documents[0].storageAvailable").value(false));
  }

  @Test
  void shouldExposeStorageAvailabilityForDocumentDetail() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentMetadataService.requireDocument("sample")).thenReturn(entity("sample"));
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(false);
    when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

    mockMvc.perform(get("/api/documents/sample"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value("sample"))
        .andExpect(jsonPath("$.actorUser").value("user-a"))
        .andExpect(jsonPath("$.storageAvailable").value(false));
  }

  @Test
  void shouldExposeEditingStatusWhenActiveEditorsExist() throws Exception {
    DocumentMetadataEntity entity = entity("sample");
    entity.setStatus("saved");
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentMetadataService.listDocuments("tenant-a", null, null, null, null, "desc"))
        .thenReturn(List.of(entity));
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
    when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 2));

    mockMvc.perform(get("/api/documents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[0].status").value("editing"));
  }

  @Test
  void shouldExposeEditingStatusForDetailWhenActiveEditorsExist() throws Exception {
    DocumentMetadataEntity entity = entity("sample");
    entity.setStatus("saved");
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentMetadataService.requireDocument("sample")).thenReturn(entity);
    when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
    when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 1));

    mockMvc.perform(get("/api/documents/sample"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("editing"));
  }

  @Test
  void shouldCreateDocumentExplicitly() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.createNativeDocument(anyString(), anyString(), any(com.earmo.onlyoffice.integration.model.RequestContext.class), anyString()))
        .thenReturn(storedDocument("doc-1", "alpha.docx", "external-1"));

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
        .andExpect(jsonPath("$.actorUser").value("user-a"))
        .andExpect(jsonPath("$.actorName").value("Alice"))
        .andExpect(jsonPath("$.storageAvailable").value(true));

    verify(documentStorageService).createNativeDocument(
        anyString(),
        anyString(),
        any(com.earmo.onlyoffice.integration.model.RequestContext.class),
        anyString()
    );
  }

  @Test
  void shouldUploadDocumentWithConsistentSummaryProjection() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.storeUploadedDocument(
        anyString(),
        any(byte[].class),
        any(com.earmo.onlyoffice.integration.model.RequestContext.class)
    )).thenReturn(storedDocument("upload-1", "roadmap.docx", null));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "roadmap.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "demo".getBytes()
    );

    mockMvc.perform(multipart("/api/documents/upload").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value("upload-1"))
        .andExpect(jsonPath("$.title").value("roadmap.docx"))
        .andExpect(jsonPath("$.ownerUser").value("user-a"))
        .andExpect(jsonPath("$.actorUser").value("user-a"))
        .andExpect(jsonPath("$.actorName").value("Alice"))
        .andExpect(jsonPath("$.storageAvailable").value(true));
  }

  @Test
  void shouldImportRemoteDocumentWithConsistentSummaryProjection() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.importRemoteDocument(
        anyString(),
        any(com.earmo.onlyoffice.integration.model.RequestContext.class)
    )).thenReturn(storedDocument("import-1", "external.docx", null));

    mockMvc.perform(post("/api/documents/import-remote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "sourceUrl": "https://files.example.test/external.docx"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value("import-1"))
        .andExpect(jsonPath("$.title").value("external.docx"))
        .andExpect(jsonPath("$.ownerUser").value("user-a"))
        .andExpect(jsonPath("$.actorUser").value("user-a"))
        .andExpect(jsonPath("$.actorName").value("Alice"))
        .andExpect(jsonPath("$.storageAvailable").value(true));
  }

  private AccessContext accessContext() {
    return new AccessContext(
        "tenant-a",
        "native",
        "user-a",
        "Alice",
        java.util.Map.of("edit", true, "download", true),
        "header"
    );
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

  private StoredDocument storedDocument(String documentId, String title, String externalDocumentId) {
    return new StoredDocument(
        documentId,
        "tenant-a",
        "user-a",
        "native",
        externalDocumentId,
        title,
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
