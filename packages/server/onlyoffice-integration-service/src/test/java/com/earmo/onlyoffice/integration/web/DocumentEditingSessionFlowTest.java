package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentEditorSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentEditingSessionFlowTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DocumentEditorSessionRepository documentEditorSessionRepository;

  @Test
  void shouldExitEditingStatusAfterClosingEditingSession() throws Exception {
    String actorUser = "session-user";
    String actorName = "Session User";
    String documentId = createDocument(actorUser, actorName);

    mockMvc.perform(
            get("/api/documents/{documentId}/editor-config", documentId)
                .header("X-Tenant-Id", "native")
                .header("X-Source-System", "native")
                .header("X-External-User-Id", actorUser)
                .header("X-User-Display-Name", actorName)
                .header("X-Access-Permissions", "edit=true,download=true,comment=true,print=true")
        )
        .andExpect(status().isOk());

    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30))).isEqualTo(1L);

    mockMvc.perform(
            post("/api/documents/{documentId}/editing-sessions/close", documentId)
                .header("X-Tenant-Id", "native")
                .header("X-Source-System", "native")
                .header("X-External-User-Id", actorUser)
                .header("X-User-Display-Name", actorName)
                .header("X-Access-Permissions", "edit=true,download=true,comment=true,print=true")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("draft"));

    assertThat(documentEditorSessionRepository.countActiveByDocumentId(documentId, Instant.now().minusSeconds(30))).isZero();

    mockMvc.perform(
            get("/api/documents/{documentId}", documentId)
                .header("X-Tenant-Id", "native")
                .header("X-Source-System", "native")
                .header("X-External-User-Id", actorUser)
                .header("X-User-Display-Name", actorName)
                .header("X-Access-Permissions", "edit=true,download=true,comment=true,print=true")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));

    mockMvc.perform(
            get("/api/documents")
                .header("X-Tenant-Id", "native")
                .header("X-Source-System", "native")
                .header("X-External-User-Id", actorUser)
                .header("X-User-Display-Name", actorName)
                .header("X-Access-Permissions", "edit=true,download=true,comment=true,print=true")
        )
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

    mockMvc.perform(
            get("/api/documents/{documentId}", documentId)
                .header("X-Tenant-Id", "native")
                .header("X-Source-System", "native")
                .header("X-External-User-Id", actorUser)
                .header("X-User-Display-Name", actorName)
                .header("X-Access-Permissions", "edit=true,download=true,comment=true,print=true")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));
  }

  private String createDocument(String actorUser, String actorName) throws Exception {
    MvcResult result = mockMvc.perform(
            post("/api/documents")
                .header("X-Tenant-Id", "native")
                .header("X-Source-System", "native")
                .header("X-External-User-Id", actorUser)
                .header("X-User-Display-Name", actorName)
                .header("X-Access-Permissions", "edit=true,download=true,comment=true,print=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "session-flow.docx"
                    }
                    """)
        )
        .andExpect(status().isOk())
        .andReturn();
    String body = result.getResponse().getContentAsString();
    int start = body.indexOf("\"documentId\":\"");
    int valueStart = start + "\"documentId\":\"".length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }
}
