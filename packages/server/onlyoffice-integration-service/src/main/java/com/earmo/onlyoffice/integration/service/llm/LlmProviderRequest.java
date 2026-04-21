package com.earmo.onlyoffice.integration.service.llm;

import java.util.List;

public record LlmProviderRequest(
    String model,
    List<LlmProviderMessage> messages
) {
}
