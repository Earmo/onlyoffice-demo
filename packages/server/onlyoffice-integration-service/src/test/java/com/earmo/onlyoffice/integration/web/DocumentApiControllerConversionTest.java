package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.data.mapper.AccessAuditEventMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentEditorSessionMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentRuntimeEventMapper;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeConversionService;
import com.earmo.onlyoffice.integration.service.OnlyofficeDocumentBuilderService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentApiController.class)
class DocumentApiControllerConversionTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean private DocumentMetadataService documentMetadataService;
  @MockBean private DocumentStorageService documentStorageService;
  @MockBean private DocumentStatusService documentStatusService;
  @MockBean private AccessAuditService accessAuditService;
  @MockBean private AccessContextResolver accessContextResolver;
  @MockBean private OnlyofficeConversionService onlyofficeConversionService;
  @MockBean private OnlyofficeDocumentBuilderService onlyofficeDocumentBuilderService;
  @MockBean private OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  @MockBean private DocumentMetadataMapper documentMetadataMapper;
  @MockBean private AccessAuditEventMapper accessAuditEventMapper;
  @MockBean private DocumentRuntimeEventMapper documentRuntimeEventMapper;
  @MockBean private DocumentEditorSessionMapper documentEditorSessionMapper;

  @Test
  void shouldConvertPdfDocumentToWord() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.getRequiredDocument("pdf-001"))
        .thenReturn(pdfStoredDocument("pdf-001", "report.pdf"));
    when(onlyofficeConversionService.convertDocument("pdf-001", "pdf", "docx"))
        .thenReturn("docx-bytes".getBytes());
    when(documentStorageService.storeUploadedDocument(
        eq("report.docx"), any(byte[].class), any()))
        .thenReturn(docxStoredDocument("docx-999", "report.docx"));

    mockMvc.perform(post("/api/documents/pdf-001/convert"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value("docx-999"))
        .andExpect(jsonPath("$.title").value("report.docx"))
        .andExpect(jsonPath("$.fileType").value("docx"));

    verify(accessAuditService).recordDocumentImported(eq("docx-999"), any());
  }

  @Test
  void shouldRejectConvertOnNonPdfDocument() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.getRequiredDocument("doc-001"))
        .thenReturn(docxStoredDocument("doc-001", "notes.docx"));

    mockMvc.perform(post("/api/documents/doc-001/convert"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("仅支持对 PDF 文档")));
  }

  @Test
  void shouldReturnErrorWhenConversionServiceFails() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.getRequiredDocument("pdf-002"))
        .thenReturn(pdfStoredDocument("pdf-002", "slides.pdf"));
    when(onlyofficeConversionService.convertDocument(anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("ONLYOFFICE Conversion API 返回错误码：-3"));

    mockMvc.perform(post("/api/documents/pdf-002/convert"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void shouldReturn404WhenDocumentNotFound() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.getRequiredDocument("not-exist"))
        .thenThrow(new IOException("文档不存在：not-exist"));

    mockMvc.perform(post("/api/documents/not-exist/convert"))
        .andExpect(status().isInternalServerError());
  }

  private AccessContext accessContext() {
    return new AccessContext(
        "tenant-a", "native", "user-a", "Alice",
        Map.of("edit", true, "download", true), "header"
    );
  }

  private StoredDocument pdfStoredDocument(String documentId, String title) {
    return new StoredDocument(
        documentId, "tenant-a", "user-a", "native", null,
        title, "documents/" + documentId + ".pdf",
        "pdf", "pdf", "draft",
        Path.of("documents/" + documentId + ".pdf"),
        Instant.parse("2026-04-14T08:00:00Z"),
        null, null, null, null
    );
  }

  private StoredDocument docxStoredDocument(String documentId, String title) {
    return new StoredDocument(
        documentId, "tenant-a", "user-a", "native", null,
        title, "documents/" + documentId + ".docx",
        "docx", "word", "draft",
        Path.of("documents/" + documentId + ".docx"),
        Instant.parse("2026-04-14T08:00:00Z"),
        null, null, null, null
    );
  }
}
