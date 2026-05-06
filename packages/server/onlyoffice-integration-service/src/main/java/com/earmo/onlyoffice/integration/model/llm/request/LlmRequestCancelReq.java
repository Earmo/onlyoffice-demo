package com.earmo.onlyoffice.integration.model.llm.request;

import jakarta.validation.constraints.NotBlank;

public record LlmRequestCancelReq(
        @NotBlank(message = "documentId 不能为空。") String documentId,
        @NotBlank(message = "requestId 不能为空。") String requestId
) {
}
