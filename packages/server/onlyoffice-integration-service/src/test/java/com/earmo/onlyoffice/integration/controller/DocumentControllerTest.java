package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.data.mapper.AccessAuditEventMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentEditorSessionMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmMessageMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmMessageVariantMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmRequestMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmSessionMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentMetadataMapper;
import com.earmo.onlyoffice.integration.data.mapper.DocumentRuntimeEventMapper;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusEventResponse;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.model.NormalizedDocumentMetadata;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentNotFoundException;
import com.earmo.onlyoffice.integration.service.DocumentRuntimeEventStreamService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeCommandService;
import com.earmo.onlyoffice.integration.service.OnlyofficeConfigService;
import com.earmo.onlyoffice.integration.service.OnlyofficeImageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

  @MockBean
  private OnlyofficeConfigService onlyofficeConfigService;

  @MockBean
  private DocumentStorageService documentStorageService;

  @MockBean
  private OnlyofficeImageService onlyofficeImageService;

  @MockBean
  private DocumentStatusService documentStatusService;

  @MockBean
  private DocumentRuntimeEventStreamService documentRuntimeEventStreamService;

  @MockBean
  private AccessAuditService accessAuditService;

  @MockBean
  private AccessContextResolver accessContextResolver;

  @MockBean
  private OnlyofficeJwtService onlyofficeJwtService;

  @MockBean
  private DocumentMetadataMapper documentMetadataMapper;

  @MockBean
  private AccessAuditEventMapper accessAuditEventMapper;

  @MockBean
  private DocumentRuntimeEventMapper documentRuntimeEventMapper;

  @MockBean
  private DocumentEditorSessionMapper documentEditorSessionMapper;

  @MockBean
  private DocumentLlmSessionMapper documentLlmSessionMapper;

  @MockBean
  private DocumentLlmRequestMapper documentLlmRequestMapper;

  @MockBean
  private DocumentLlmMessageMapper documentLlmMessageMapper;

  @MockBean
  private DocumentLlmMessageVariantMapper documentLlmMessageVariantMapper;

  @MockBean
  private OnlyofficeCommandService onlyofficeCommandService;

  @Test
  void shouldPersistCallbackDocumentWhenStatusIs2() throws Exception {
    when(onlyofficeJwtService.verifyCallbackRequest(any())).thenReturn(mock(io.jsonwebtoken.Claims.class));
    when(documentStorageService.saveCallbackDocument("sample", "https://files.example.test/latest.docx", "docx"))
        .thenReturn(new NormalizedDocumentMetadata("sample.docx", "docx", "word"));

    mockMvc.perform(post("/api/documents/sample/callback")
            .header("Authorization", "Bearer signed-callback-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": 2,
                  "url": "https://files.example.test/latest.docx",
                  "filetype": "docx"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(0));

    verify(onlyofficeJwtService).verifyCallbackRequest(org.mockito.ArgumentMatchers.any());
    verify(documentStatusService).recordCallbackReceived("sample", 2);
    verify(documentStorageService).saveCallbackDocument("sample", "https://files.example.test/latest.docx", "docx");
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

    verify(documentStatusService).openEditingSession(anyString(), org.mockito.ArgumentMatchers.any(AccessContext.class));
  }

  @Test
  void shouldBuildReadonlyPreviewConfigWithoutOpeningEditingSession() throws Exception {
    when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(
        new AccessContext(
            "tenant-a",
            "native",
            "user-a",
            "Alice",
            java.util.Map.of("edit", true, "download", true),
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
            "document", java.util.Map.of("permissions", java.util.Map.of("edit", false)),
            "editorConfig", java.util.Map.of("mode", "view")
        )
    ));

    mockMvc.perform(get("/api/documents/sample/editor-config").param("readonly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.config.editorConfig.mode").value("view"));

    verify(documentStatusService).initialize("sample");
  }

  @Test
  void shouldMarkDocumentFailedWhenCallbackWriteBackFails() throws Exception {
    when(onlyofficeJwtService.verifyCallbackRequest(any())).thenReturn(mock(io.jsonwebtoken.Claims.class));
    when(documentStorageService.saveCallbackDocument("sample", "https://files.example.test/latest.docx", "docx"))
        .thenThrow(new IOException("storage failed"));

    mockMvc.perform(post("/api/documents/sample/callback")
            .header("Authorization", "Bearer signed-callback-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": 2,
                  "url": "https://files.example.test/latest.docx",
                  "filetype": "docx"
                }
                """))
        .andExpect(status().is5xxServerError());

    verify(documentStatusService).recordCallbackReceived("sample", 2);
    verify(documentStatusService).recordSaveFailed("sample", 2, "storage failed");
  }

  @Test
  void shouldRejectCallbackWhenJwtIsMissingOrInvalid() throws Exception {
    doThrow(new IllegalArgumentException("ONLYOFFICE callback JWT 校验失败：签名无效。"))
        .when(onlyofficeJwtService)
        .verifyCallbackRequest(org.mockito.ArgumentMatchers.any());

    mockMvc.perform(post("/api/documents/sample/callback")
            .header("Authorization", "Bearer invalid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": 2,
                  "url": "https://files.example.test/latest.docx"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("ONLYOFFICE callback JWT 校验失败：签名无效。"));

    verify(documentStatusService).recordCallbackRejected("sample", "ONLYOFFICE callback JWT 校验失败：签名无效。");
    verify(accessAuditService).recordCallbackRejected("sample", "ONLYOFFICE callback JWT 校验失败：签名无效。");
  }

  @Test
  void shouldExposeSharedSaveStatusProjection() throws Exception {
    when(documentStatusService.getStatus("sample")).thenReturn(new DocumentSaveStatusResponse(
        "sample",
        "saved",
        "最新修改已成功回写到共享存储。",
        2,
        Instant.parse("2026-03-25T10:00:00Z"),
        Instant.parse("2026-03-25T10:00:01Z"),
        List.of(new DocumentSaveStatusEventResponse(
            "save_succeeded",
            "最新修改已成功回写到共享存储。",
            2,
            Instant.parse("2026-03-25T10:00:01Z")
        ))
    ));

    mockMvc.perform(get("/api/documents/sample/save-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("saved"))
        .andExpect(jsonPath("$.recentEvents[0].eventType").value("save_succeeded"))
        .andExpect(jsonPath("$.recentEvents[0].callbackStatus").value(2));
  }

  @Test
  void shouldOpenRuntimeEventStreamWithInitialSaveStatus() throws Exception {
    AccessContext accessContext =
        new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of("edit", true), "header");
    DocumentSaveStatusResponse initialStatus = new DocumentSaveStatusResponse(
        "sample",
        "saved",
        "最新修改已成功回写到共享存储。",
        2,
        Instant.parse("2026-03-25T10:00:00Z"),
        Instant.parse("2026-03-25T10:00:01Z"),
        List.of()
    );
    when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(accessContext);
    when(documentStatusService.getStatus("sample")).thenReturn(initialStatus);
    when(documentRuntimeEventStreamService.open(
        org.mockito.ArgumentMatchers.eq("sample"),
        org.mockito.ArgumentMatchers.eq(accessContext),
        org.mockito.ArgumentMatchers.eq(initialStatus),
        org.mockito.ArgumentMatchers.any(Runnable.class)
    )).thenAnswer(invocation -> {
      SseEmitter emitter = new SseEmitter(180000L);
      emitter.send(SseEmitter.event().name("save-status").data(initialStatus));
      emitter.complete();
      return emitter;
    });

    MvcResult mvcResult = mockMvc.perform(get("/api/documents/sample/runtime-events"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("event:save-status")));

    verify(accessContextResolver).resolve(org.mockito.ArgumentMatchers.any());
    verify(documentStatusService).touchEditingSession("sample", accessContext);
    verify(documentStatusService).getStatus("sample");
    verify(documentRuntimeEventStreamService).open(
        org.mockito.ArgumentMatchers.eq("sample"),
        org.mockito.ArgumentMatchers.eq(accessContext),
        argThat(statusResponse -> statusResponse != null && "sample".equals(statusResponse.documentId())),
        org.mockito.ArgumentMatchers.any(Runnable.class)
    );
  }

  @Test
  void shouldCloseEditingSessionForCurrentActor() throws Exception {
    when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(
        new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of("edit", true), "header")
    );
    when(documentStatusService.closeEditingSession(anyString(), org.mockito.ArgumentMatchers.any(AccessContext.class)))
        .thenReturn(new DocumentSaveStatusResponse(
            "sample",
            "saved",
            "当前用户已离开编辑器，文档已退出活跃编辑状态。",
            2,
            Instant.parse("2026-03-25T10:00:00Z"),
            Instant.parse("2026-03-25T10:00:01Z"),
            List.of()
        ));

    mockMvc.perform(post("/api/documents/sample/editing-sessions/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("saved"))
        .andExpect(jsonPath("$.message").value("当前用户已离开编辑器，文档已退出活跃编辑状态。"));

    verify(documentStatusService).closeEditingSession(anyString(), org.mockito.ArgumentMatchers.any(AccessContext.class));
  }

  @Test
  void shouldNotExposeEditingSessionHeartbeatEndpoint() {
    assertThat(requestMappingHandlerMapping.getHandlerMethods().keySet())
        .flatExtracting(mappingInfo -> mappingInfo.getPatternValues())
        .noneMatch(pattern -> pattern.contains("/editing-sessions/heartbeat"));
  }

  @Test
  void shouldTriggerForceSaveAndReturnLatestStatus() throws Exception {
    when(documentStatusService.getStatus("sample")).thenReturn(new DocumentSaveStatusResponse(
        "sample",
        "saved",
        "最新修改已成功回写到共享存储。",
        6,
        Instant.parse("2026-03-25T10:00:02Z"),
        Instant.parse("2026-03-25T10:00:03Z"),
        List.of()
    ));
    when(onlyofficeCommandService.forceSaveAndAwait("sample", 8000L)).thenReturn(true);

    mockMvc.perform(post("/api/documents/sample/save"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("saved"))
        .andExpect(jsonPath("$.lastCallbackStatus").value(6));

    verify(onlyofficeCommandService).forceSaveAndAwait("sample", 8000L);
    verify(documentStatusService).getStatus("sample");
  }

  @Test
  void shouldReturnExplicitErrorWhenEditorConfigFailsFastOnRuntimeUrls() throws Exception {
    when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(
        new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of("edit", true), "header")
    );
    when(onlyofficeConfigService.buildEditorConfig(
        anyString(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.any(AccessContext.class),
        org.mockito.ArgumentMatchers.any()
    )).thenThrow(new IllegalStateException("ONLYOFFICE 运行配置缺失：onlyoffice.integration.document-server-url 不能为空。"));

    mockMvc.perform(get("/api/documents/sample/editor-config"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("服务端处理失败，请稍后重试。"));
  }

  @Test
  void shouldRejectArchivedDocumentWhenOpeningEditorConfig() throws Exception {
    when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(
        new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of("edit", true), "header")
    );
    when(documentStatusService.openEditingSession(anyString(), org.mockito.ArgumentMatchers.any(AccessContext.class)))
        .thenThrow(new DocumentNotFoundException("archived"));

    mockMvc.perform(get("/api/documents/archived/editor-config"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("文档不存在：archived"));
  }

  @Test
  void shouldRejectArchivedDocumentWhenDownloadingFile() throws Exception {
    when(documentStorageService.getRequiredDocument("archived"))
        .thenThrow(new DocumentNotFoundException("archived"));

    mockMvc.perform(get("/api/documents/archived/file"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("文档不存在：archived"));
  }

  @Test
  void shouldRejectArchivedDocumentWhenReadingSaveStatus() throws Exception {
    when(documentStatusService.getStatus("archived"))
        .thenThrow(new DocumentNotFoundException("archived"));

    mockMvc.perform(get("/api/documents/archived/save-status"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("文档不存在：archived"));
  }

  @Test
  void shouldDownloadDocumentViaExtendedFileRoute() throws Exception {
    when(documentStorageService.getRequiredDocument("sample")).thenReturn(new com.earmo.onlyoffice.integration.model.StoredDocument(
        "sample",
        "tenant-a",
        "user-a",
        "native",
        null,
        "report.docx",
        "documents/report.docx",
        "docx",
        "word",
        "draft",
        java.nio.file.Path.of("report.docx"),
        Instant.parse("2026-03-25T10:00:00Z"),
        null,
        null,
        null,
        null
    ));
    when(documentStorageService.readDocument("sample")).thenReturn("demo".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc.perform(get("/api/documents/sample/file.docx"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''report.docx")));
  }
}
