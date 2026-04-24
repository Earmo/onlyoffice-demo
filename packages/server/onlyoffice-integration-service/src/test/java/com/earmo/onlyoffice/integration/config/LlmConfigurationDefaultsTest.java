package com.earmo.onlyoffice.integration.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmConfigurationDefaultsTest {

  @Test
  void shouldKeepLlmDisabledByDefaultInSharedApplicationConfig() throws IOException {
    String content = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

    assertTrue(content.contains("enabled: ${LLM_ENABLED:true}"));
  }
}
