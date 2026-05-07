package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextAspect;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.data.mapper.*;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentApiController.class)
@Import(AccessContextAspect.class)
@EnableAspectJAutoProxy
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

    @MockBean
    private DocumentLlmMessageMapper documentLlmMessageMapper;

    @MockBean
    private DocumentLlmMessageVariantMapper documentLlmMessageVariantMapper;

    @MockBean
    private DocumentLlmRequestMapper documentLlmRequestMapper;

    @MockBean
    private DocumentLlmSessionMapper documentLlmSessionMapper;

    @Test
    void shouldListDocumentsForCurrentTenant() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listDocumentPage("tenant-a", null, null, null, null, "desc", 1, 10))
                .thenReturn(page(List.of(entity("sample")), 1, 10, 1));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(post("/api/documents/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.result[0].documentId").value("sample"))
                .andExpect(jsonPath("$.data.result[0].tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data.result[0].actorUser").value("user-a"))
                .andExpect(jsonPath("$.data.result[0].actorName").value("Alice"))
                .andExpect(jsonPath("$.data.result[0].sourceSystem").value("native"))
                .andExpect(jsonPath("$.data.result[0].lastEditedTime").value("2026-03-19T08:00:00Z"))
                .andExpect(jsonPath("$.data.result[0].storageAvailable").value(true));

        verify(documentMetadataService).listDocumentPage("tenant-a", null, null, null, null, "desc", 1, 10);
    }

    @Test
    void shouldForwardQueryAndFilterParameters() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listDocumentPage("tenant-a", "roadmap", "failed", "native", "word", "asc", 2, 20))
                .thenReturn(page(List.of(entity("sample")), 2, 20, 21));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(
                        post("/api/documents/page")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "query": "roadmap",
                                          "status": "failed",
                                          "sourceSystem": "native",
                                          "documentType": "word",
                                          "sortDirection": "asc",
                                          "pageNumber": 2,
                                          "pageSize": 20
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(21))
                .andExpect(jsonPath("$.data.result[0].documentId").value("sample"))
                .andExpect(jsonPath("$.data.result[0].lastEditedTime").value("2026-03-19T08:00:00Z"));

        verify(documentMetadataService).listDocumentPage("tenant-a", "roadmap", "failed", "native", "word", "asc", 2, 20);
    }

    @Test
    void shouldAcceptTextPlainJsonForDocumentPage() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listDocumentPage("tenant-a", "roadmap", null, null, null, "desc", 2, 20))
                .thenReturn(page(List.of(entity("sample")), 2, 20, 21));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(post("/api/documents/page")
                        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                        .content("""
                                {
                                  "query": "roadmap",
                                  "pageNumber": 2,
                                  "pageSize": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(21))
                .andExpect(jsonPath("$.data.result[0].documentId").value("sample"));

        verify(documentMetadataService).listDocumentPage("tenant-a", "roadmap", null, null, null, "desc", 2, 20);
    }

    @Test
    void shouldListRecentDocumentsIndependentFromPagination() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listRecentDocuments("tenant-a", 2))
                .thenReturn(List.of(entity("recent-2", Instant.parse("2026-03-19T09:00:00Z")), entity("recent-1")));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("recent-2", "recent-1")))
                .thenReturn(java.util.Map.of("recent-2", 0, "recent-1", 0));

        mockMvc.perform(post("/api/documents/list/recent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "limit": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentId").value("recent-2"))
                .andExpect(jsonPath("$.data[0].lastEditedTime").value("2026-03-19T09:00:00Z"))
                .andExpect(jsonPath("$.data[1].documentId").value("recent-1"));

        verify(documentMetadataService).listRecentDocuments("tenant-a", 2);
    }

    @Test
    void shouldAcceptTextPlainJsonForRecentDocuments() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listRecentDocuments("tenant-a", 2))
                .thenReturn(List.of(entity("recent-2", Instant.parse("2026-03-19T09:00:00Z")), entity("recent-1")));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("recent-2", "recent-1")))
                .thenReturn(java.util.Map.of("recent-2", 0, "recent-1", 0));

        mockMvc.perform(post("/api/documents/list/recent")
                        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                        .content("""
                                {
                                  "limit": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentId").value("recent-2"))
                .andExpect(jsonPath("$.data[0].lastEditedTime").value("2026-03-19T09:00:00Z"))
                .andExpect(jsonPath("$.data[1].documentId").value("recent-1"));

        verify(documentMetadataService).listRecentDocuments("tenant-a", 2);
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

        mockMvc.perform(post("/api/documents/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storage": "unavailable"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.result.length()").value(1))
                .andExpect(jsonPath("$.data.result[0].documentId").value("missing"))
                .andExpect(jsonPath("$.data.result[0].storageAvailable").value(false));
    }

    @Test
    void shouldExposeStorageAvailabilityForDocumentDetail() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.requireAccessibleDocument("sample")).thenReturn(entity("sample"));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(false);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(post("/api/documents/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "sample"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("sample"))
                .andExpect(jsonPath("$.data.actorUser").value("user-a"))
                .andExpect(jsonPath("$.data.lastEditedTime").value("2026-03-19T08:00:00Z"))
                .andExpect(jsonPath("$.data.storageAvailable").value(false));
    }

    @Test
    void shouldExposeEditingStatusWhenActiveEditorsExist() throws Exception {
        DocumentMetadataEntity entity = entity("sample");
        entity.setStatus("saved");
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listDocumentPage("tenant-a", null, null, null, null, "desc", 1, 10))
                .thenReturn(page(List.of(entity), 1, 10, 1));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 2));

        mockMvc.perform(post("/api/documents/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result[0].status").value("editing"));
    }

    @Test
    void shouldExposePersistedStatusWhenNoActiveEditorsRemainInList() throws Exception {
        DocumentMetadataEntity entity = entity("sample");
        entity.setStatus("saved");
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.listDocumentPage("tenant-a", null, null, null, null, "desc", 1, 10))
                .thenReturn(page(List.of(entity), 1, 10, 1));
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(post("/api/documents/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result[0].status").value("saved"));
    }

    @Test
    void shouldExposeEditingStatusForDetailWhenActiveEditorsExist() throws Exception {
        DocumentMetadataEntity entity = entity("sample");
        entity.setStatus("saved");
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.requireAccessibleDocument("sample")).thenReturn(entity);
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 1));

        mockMvc.perform(post("/api/documents/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "sample"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("editing"));
    }

    @Test
    void shouldExposePersistedStatusForDetailWhenNoActiveEditorsRemain() throws Exception {
        DocumentMetadataEntity entity = entity("sample");
        entity.setStatus("saved");
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentMetadataService.requireAccessibleDocument("sample")).thenReturn(entity);
        when(documentStorageService.exists(any(DocumentMetadataEntity.class))).thenReturn(true);
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(post("/api/documents/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "sample"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("saved"));
    }

    @Test
    void shouldCreateDocumentExplicitly() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentStorageService.createNativeDocument(
                nullable(String.class),
                anyString(),
                any(com.earmo.onlyoffice.integration.model.RequestContext.class),
                anyString()
        ))
                .thenReturn(storedDocument("01ARZ3NDEKTSV4RRFFQ69G5FAV", "alpha.docx", "external-1"));

        mockMvc.perform(post("/api/documents/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "doc-1",
                                  "title": "alpha.docx",
                                  "externalDocumentId": "external-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("01ARZ3NDEKTSV4RRFFQ69G5FAV"))
                .andExpect(jsonPath("$.data.title").value("alpha.docx"))
                .andExpect(jsonPath("$.data.ownerUser").value("user-a"))
                .andExpect(jsonPath("$.data.actorUser").value("user-a"))
                .andExpect(jsonPath("$.data.actorName").value("Alice"))
                .andExpect(jsonPath("$.data.lastEditedTime").value("2026-03-19T08:00:00Z"))
                .andExpect(jsonPath("$.data.storageAvailable").value(true));

        verify(documentStorageService).createNativeDocument(
                isNull(),
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
        )).thenReturn(storedDocument("01ARZ3NDEKTSV4RRFFQ69G5FAA", "roadmap.docx", null));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "roadmap.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "demo".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("01ARZ3NDEKTSV4RRFFQ69G5FAA"))
                .andExpect(jsonPath("$.data.title").value("roadmap.docx"))
                .andExpect(jsonPath("$.data.ownerUser").value("user-a"))
                .andExpect(jsonPath("$.data.actorUser").value("user-a"))
                .andExpect(jsonPath("$.data.actorName").value("Alice"))
                .andExpect(jsonPath("$.data.lastEditedTime").value("2026-03-19T08:00:00Z"))
                .andExpect(jsonPath("$.data.storageAvailable").value(true));
    }

    @Test
    void shouldImportRemoteDocumentWithConsistentSummaryProjection() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentStorageService.importRemoteDocument(
                anyString(),
                any(com.earmo.onlyoffice.integration.model.RequestContext.class)
        )).thenReturn(storedDocument("01ARZ3NDEKTSV4RRFFQ69G5FAB", "external.docx", null));

        mockMvc.perform(post("/api/documents/import-remote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://files.example.test/external.docx"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("01ARZ3NDEKTSV4RRFFQ69G5FAB"))
                .andExpect(jsonPath("$.data.title").value("external.docx"))
                .andExpect(jsonPath("$.data.ownerUser").value("user-a"))
                .andExpect(jsonPath("$.data.actorUser").value("user-a"))
                .andExpect(jsonPath("$.data.actorName").value("Alice"))
                .andExpect(jsonPath("$.data.lastEditedTime").value("2026-03-19T08:00:00Z"))
                .andExpect(jsonPath("$.data.storageAvailable").value(true));
    }

    @Test
    void shouldArchiveDocumentWhenNoActiveEditorsRemain() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 0));

        mockMvc.perform(post("/api/documents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "sample"
                                }
                                """))
                .andExpect(status().isOk());

        verify(documentMetadataService).archiveDocument("sample");
        verify(accessAuditService).recordDocumentArchived("sample");
    }

    @Test
    void shouldRejectArchiveWhenDocumentStillHasActiveEditors() throws Exception {
        when(accessContextResolver.resolve(any())).thenReturn(accessContext());
        when(documentStatusService.countActiveEditingSessions(List.of("sample"))).thenReturn(java.util.Map.of("sample", 1));

        mockMvc.perform(post("/api/documents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "sample"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("文档仍有活跃编辑会话，暂时不能删除。"));
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
        return entity(documentId, Instant.parse("2026-03-19T08:00:00Z"));
    }

    private DocumentMetadataEntity entity(String documentId, Instant updatedTime) {
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
        entity.setUpdatedTime(updatedTime);
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

    private Page<DocumentMetadataEntity> page(
            List<DocumentMetadataEntity> records,
            long pageNumber,
            long pageSize,
            long total
    ) {
        return new Page<>(records, pageNumber, pageSize, total);
    }
}
