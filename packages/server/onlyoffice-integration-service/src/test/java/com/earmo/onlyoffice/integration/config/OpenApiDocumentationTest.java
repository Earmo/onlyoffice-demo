package com.earmo.onlyoffice.integration.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldExposeGroupedOpenApiDocument() throws Exception {
    mockMvc.perform(get("/v3/api-docs/document-service"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.1.0"));
  }

  @Test
  void shouldExposeDocumentationEntryPage() throws Exception {
    mockMvc.perform(get("/doc.html"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/html")));
  }
}
