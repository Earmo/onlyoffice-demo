package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.data.mapper.AccessAuditEventMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeConfigService;
import com.earmo.onlyoffice.integration.service.OnlyofficeImageService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private OnlyofficeConfigService onlyofficeConfigService;

  @MockBean
  private DocumentStorageService documentStorageService;

  @MockBean
  private OnlyofficeImageService onlyofficeImageService;

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

  @Test
  void shouldPersistCallbackDocumentWhenStatusIs2() throws Exception {
    doNothing().when(documentStorageService).saveCallbackDocument("sample", "https://files.example.test/latest.docx");

    mockMvc.perform(post("/api/documents/sample/callback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": 2,
                  "url": "https://files.example.test/latest.docx"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(0));

    verify(documentStatusService).recordCallbackReceived("sample", 2);
    verify(documentStorageService).saveCallbackDocument("sample", "https://files.example.test/latest.docx");
    verify(documentStatusService).recordSaveSucceeded("sample", 2);
  }

  @Test
  void shouldBuildEditorConfigWithAccessContext() throws Exception {
    when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(
        new AccessContext(
            "tenant-a",
            "native",
            "user-a",
            "Alice",
            java.util.Map.of("edit", false, "download", true, "comment", true, "print", false),
            "header"
        )
    );
    when(onlyofficeConfigService.buildEditorConfig(
        anyString(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.any(AccessContext.class),
        org.mockito.ArgumentMatchers.any()
    )).thenReturn(new EditorConfigResponse(
        "https://docs.example.test/",
        java.util.Map.of(
            "document", java.util.Map.of(
                "permissions", java.util.Map.of("edit", false, "download", true, "comment", true, "print", false)
            ),
            "editorConfig", java.util.Map.of(
                "mode", "view",
                "user", java.util.Map.of("id", "user-a", "name", "Alice")
            )
        )
    ));

    mockMvc.perform(get("/api/documents/sample/editor-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.config.editorConfig.user.id").value("user-a"))
        .andExpect(jsonPath("$.config.editorConfig.user.name").value("Alice"))
        .andExpect(jsonPath("$.config.editorConfig.mode").value("view"))
        .andExpect(jsonPath("$.config.document.permissions.download").value(true))
        .andExpect(jsonPath("$.config.document.permissions.comment").value(true));

    verify(documentStatusService).initialize("sample");
  }

  @Test
  void shouldMarkDocumentFailedWhenCallbackWriteBackFails() throws Exception {
    doThrow(new IOException("storage failed"))
        .when(documentStorageService)
        .saveCallbackDocument("sample", "https://files.example.test/latest.docx");

    mockMvc.perform(post("/api/documents/sample/callback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": 2,
                  "url": "https://files.example.test/latest.docx"
                }
                """))
        .andExpect(status().is5xxServerError());

    verify(documentStatusService).recordCallbackReceived("sample", 2);
    verify(documentStatusService).recordSaveFailed("sample", 2, "storage failed");
  }
}
