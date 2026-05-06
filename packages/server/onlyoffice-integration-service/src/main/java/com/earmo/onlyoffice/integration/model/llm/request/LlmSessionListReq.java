package com.earmo.onlyoffice.integration.model.llm.request;

import jakarta.validation.constraints.NotBlank;

public record LlmSessionListReq(@NotBlank(message = "documentId 不能为空。") String documentId) {
}
