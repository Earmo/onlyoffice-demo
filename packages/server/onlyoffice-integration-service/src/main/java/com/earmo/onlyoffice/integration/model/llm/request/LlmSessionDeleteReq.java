package com.earmo.onlyoffice.integration.model.llm.request;

import jakarta.validation.constraints.NotBlank;

public record LlmSessionDeleteReq(
        @NotBlank(message = "documentId 不能为空。") String documentId,
        @NotBlank(message = "sessionId 不能为空。") String sessionId
) {
}
