package com.earmo.onlyoffice.integration.model.llm;

import jakarta.validation.constraints.NotBlank;

public record LlmCapabilityReq(@NotBlank(message = "documentId 不能为空。") String documentId) {
}
