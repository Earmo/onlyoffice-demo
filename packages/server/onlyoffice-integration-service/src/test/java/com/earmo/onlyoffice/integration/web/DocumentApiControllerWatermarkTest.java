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
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentApiController.class)
class DocumentApiControllerWatermarkTest {

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
  void shouldReturnWatermarkScriptAsJavaScript() throws Exception {
    when(documentStorageService.getRequiredDocument("doc-001"))
        .thenReturn(docxStoredDocument("doc-001", "report.docx"));
    when(onlyofficeDocumentBuilderService.generateRemoveWatermarkScript("doc-001", "docx"))
        .thenReturn("""
            builder.OpenFile("http://backend:8080/api/documents/doc-001/file.docx", "");
            var oDocument = Api.GetDocument();
            oDocument.RemoveWatermark();
            builder.SaveFile("docx", "output.docx");
            builder.CloseFile();
            """);

    mockMvc.perform(get("/api/documents/doc-001/watermark-script"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/javascript"))
        .andExpect(content().string(containsString("RemoveWatermark")))
        .andExpect(content().string(containsString("builder.OpenFile")));
  }

  @Test
  void shouldRemoveWatermarkFromDocxDocument() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(onlyofficeIntegrationProperties.getInternalBaseUrl()).thenReturn("http://backend:8080");
    when(documentStorageService.getRequiredDocument("doc-001"))
        .thenReturn(docxStoredDocument("doc-001", "report.docx"));
    when(onlyofficeDocumentBuilderService.runScript(anyString(), anyString()))
        .thenReturn("processed-bytes".getBytes());
    when(documentStorageService.storeUploadedDocument(anyString(), any(byte[].class), any()))
        .thenReturn(docxStoredDocument("doc-001", "report.docx"));

    mockMvc.perform(post("/api/documents/doc-001/remove-watermark"))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldRejectRemoveWatermarkOnPdfDocument() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.getRequiredDocument("pdf-001"))
        .thenReturn(pdfStoredDocument("pdf-001", "report.pdf"));

    mockMvc.perform(post("/api/documents/pdf-001/remove-watermark"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("仅支持对 Word 格式文档")));
  }

  @Test
  void shouldRejectRemoveWatermarkOnCellDocument() throws Exception {
    when(accessContextResolver.resolve(any())).thenReturn(accessContext());
    when(documentStorageService.getRequiredDocument("xlsx-001"))
        .thenReturn(xlsxStoredDocument("xlsx-001", "data.xlsx"));

    mockMvc.perform(post("/api/documents/xlsx-001/remove-watermark"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("仅支持对 Word 格式文档")));
  }

  private AccessContext accessContext() {
    return new AccessContext(
        "tenant-a", "native", "user-a", "Alice",
        Map.of("edit", true, "download", true), "header"
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

  private StoredDocument xlsxStoredDocument(String documentId, String title) {
    return new StoredDocument(
        documentId, "tenant-a", "user-a", "native", null,
        title, "documents/" + documentId + ".xlsx",
        "xlsx", "cell", "draft",
        Path.of("documents/" + documentId + ".xlsx"),
        Instant.parse("2026-04-14T08:00:00Z"),
        null, null, null, null
    );
  }
}
