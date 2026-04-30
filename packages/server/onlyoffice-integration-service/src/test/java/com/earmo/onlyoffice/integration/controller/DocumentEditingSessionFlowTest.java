package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentEditorSessionRepository;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentEditingSessionFlowTest {

  private static final String TENANT_ID = "native";
  private static final String SOURCE_SYSTEM = "native";
  private static final String ACCESS_PERMISSIONS = "edit=true,download=true,comment=true,print=true";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DocumentEditorSessionRepository documentEditorSessionRepository;

  @Autowired
  private OnlyofficeJwtService onlyofficeJwtService;

  @Test
  void shouldKeepEditingSessionActiveAcrossRuntimeEventsDisconnectUntilExplicitClose() throws Exception {
    String actorUser = "runtime-user";
    String actorName = "Runtime User";
    String documentId = createDocument(actorUser, actorName);

    mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}/editor-config", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk());

    DocumentEditorSessionEntity sessionBeforeStream = loadActiveSession(documentId, actorUser);
    Instant lastSeenBeforeStream = sessionBeforeStream.getLastSeenTime();

    MvcResult runtimeEvents = mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}/runtime-events", documentId),
            actorUser,
            actorName
        ))
        .andExpect(request().asyncStarted())
        .andReturn();

    String initialFrame = waitForRuntimeFrame(runtimeEvents);
    assertThat(initialFrame)
        .contains("event:save-status")
        .contains("event:session-active");

    DocumentEditorSessionEntity sessionAfterStreamOpened = loadActiveSession(documentId, actorUser);
    assertThat(sessionAfterStreamOpened.getLastSeenTime()).isAfterOrEqualTo(lastSeenBeforeStream);
    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30)))
        .isEqualTo(1L);

    runtimeEvents.getRequest().getAsyncContext().complete();

    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30)))
        .isEqualTo(1L);

    mockMvc.perform(withAccessHeaders(
            post("/api/documents/{documentId}/editing-sessions/close", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("draft"));

    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30)))
        .isZero();
  }

  @Test
  void shouldExitEditingStatusAfterClosingEditingSession() throws Exception {
    String actorUser = "session-user";
    String actorName = "Session User";
    String documentId = createDocument(actorUser, actorName);

    mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}/editor-config", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk());

    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30))).isEqualTo(1L);

    mockMvc.perform(withAccessHeaders(
            post("/api/documents/{documentId}/editing-sessions/close", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("draft"));

    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30))).isZero();

    mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));

    mockMvc.perform(withAccessHeaders(get("/api/documents"), actorUser, actorName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[?(@.documentId=='" + documentId + "')].status").value("draft"));
  }

  @Test
  void shouldIgnoreStaleEditingSessionWhenProjectingListStatus() throws Exception {
    String actorUser = "stale-user";
    String actorName = "Stale User";
    String documentId = createDocument(actorUser, actorName);

    Instant staleTime = Instant.now().minusSeconds(120);
    DocumentEditorSessionEntity session = new DocumentEditorSessionEntity();
    session.setSessionId(UUID.randomUUID().toString());
    session.setDocumentId(documentId);
    session.setTenantId("native");
    session.setActorUser(actorUser);
    session.setActorName(actorName);
    session.setOpenedTime(staleTime);
    session.setLastSeenTime(staleTime);
    session.setCreatedTime(staleTime);
    session.setUpdatedTime(staleTime);
    documentEditorSessionRepository.insert(session);

    mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));
  }

  @Test
  void shouldNotProjectEditingAfterCloseWhenOnlyofficeSendsStatus4Callback() throws Exception {
    String actorUser = "callback4-user";
    String actorName = "Callback Four";
    String documentId = createDocument(actorUser, actorName);

    mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}/editor-config", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk());

    mockMvc.perform(withAccessHeaders(
            post("/api/documents/{documentId}/editing-sessions/close", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk());

    mockMvc.perform(
            post("/api/documents/{documentId}/callback", documentId)
                .header("Authorization", "Bearer " + onlyofficeJwtService.sign(Map.of("documentId", documentId, "status", 4)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": 4
                    }
                    """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(0));

    mockMvc.perform(withAccessHeaders(
            get("/api/documents/{documentId}", documentId),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));

    mockMvc.perform(withAccessHeaders(get("/api/documents"), actorUser, actorName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[?(@.documentId=='" + documentId + "')].status").value("draft"));
  }

  private String createDocument(String actorUser, String actorName) throws Exception {
    MvcResult result = mockMvc.perform(withAccessHeaders(
            post("/api/documents/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "session-flow.docx"
                    }
                    """),
            actorUser,
            actorName
        ))
        .andExpect(status().isOk())
        .andReturn();
    String body = result.getResponse().getContentAsString();
    int start = body.indexOf("\"documentId\":\"");
    int valueStart = start + "\"documentId\":\"".length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }

  private DocumentEditorSessionEntity loadActiveSession(String documentId, String actorUser) {
    return documentEditorSessionRepository.findActiveByDocumentIdAndActorUser(documentId, actorUser)
        .orElseThrow();
  }

  private String waitForRuntimeFrame(MvcResult runtimeEvents) throws Exception {
    for (int attempt = 0; attempt < 40; attempt++) {
      String body = runtimeEvents.getResponse().getContentAsString(StandardCharsets.UTF_8);
      if (body.contains("event:save-status") || body.contains("event:session-active")) {
        return body;
      }
      Thread.sleep(50L);
    }
    return runtimeEvents.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private MockHttpServletRequestBuilder withAccessHeaders(
      MockHttpServletRequestBuilder requestBuilder,
      String actorUser,
      String actorName
  ) {
    return requestBuilder
        .header("X-Tenant-Id", TENANT_ID)
        .header("X-Source-System", SOURCE_SYSTEM)
        .header("X-External-User-Id", actorUser)
        .header("X-User-Display-Name", actorName)
        .header("X-Access-Permissions", ACCESS_PERMISSIONS);
  }
}
