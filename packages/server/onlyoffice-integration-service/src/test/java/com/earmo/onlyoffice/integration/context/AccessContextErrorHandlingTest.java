package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.web.DocumentApiController;
import com.earmo.onlyoffice.integration.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessContextErrorHandlingTest {

  private DocumentMetadataService documentMetadataService;
  private DocumentStatusService documentStatusService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    documentMetadataService = mock(DocumentMetadataService.class);
    documentStatusService = mock(DocumentStatusService.class);
    DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
    AccessAuditService accessAuditService = mock(AccessAuditService.class);

    OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
    properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");
    properties.getAccessContext().setRequireExplicitContext(true);
    properties.getAccessContext().setAllowDefaultContext(true);

    AccessContextResolver accessContextResolver = new AccessContextResolver(
        properties,
        List.of(
            new HeaderAccessContextProvider(properties),
            new JwtAccessContextProvider(properties),
            new DefaultAccessContextProvider(properties)
        )
    );
    DocumentApiController controller = new DocumentApiController(
        documentMetadataService,
        documentStorageService,
        documentStatusService,
        accessAuditService,
        accessContextResolver
    );
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void shouldReturn4xxWhenContextIsCompletelyMissing() throws Exception {
    when(documentMetadataService.listDocuments(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/documents"))
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("缺少用户上下文")));
  }

  @Test
  void shouldAllowPartialContextWhenDefaultFillIsEnabled() throws Exception {
    when(documentMetadataService.listDocuments("native", null, null, null, null, "desc")).thenReturn(List.of());
    when(documentStatusService.countActiveEditingSessions(List.of())).thenReturn(java.util.Map.of());

    mockMvc.perform(get("/api/documents")
            .header("X-External-User-Id", "user-a")
            .header("X-User-Display-Name", "Alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents").isArray());
  }

  @Test
  void shouldReturn4xxWhenJwtIsInvalid() throws Exception {
    mockMvc.perform(get("/api/documents").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("访问上下文解析失败")));
  }
}
