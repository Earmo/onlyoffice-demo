package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
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
  private RequestContextResolver requestContextResolver;

  @MockBean
  private DocumentMetadataMapper documentMetadataMapper;

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
