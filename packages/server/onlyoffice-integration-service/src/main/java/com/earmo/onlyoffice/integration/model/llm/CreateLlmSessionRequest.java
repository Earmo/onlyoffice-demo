package com.earmo.onlyoffice.integration.model.llm;

import jakarta.validation.constraints.NotBlank;

public record CreateLlmSessionRequest(
    @NotBlank
    String documentId,
    String title
) {
}
