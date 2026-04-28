package com.earmo.onlyoffice.integration.model.llm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetLlmActiveVariantRequest(
    @NotBlank
    @Size(max = 256)
    String documentId,
    @NotBlank
    @Size(max = 256)
    String sessionId,
    @Size(max = 128)
    String variantId,
    Integer variantIndex
) {
}
