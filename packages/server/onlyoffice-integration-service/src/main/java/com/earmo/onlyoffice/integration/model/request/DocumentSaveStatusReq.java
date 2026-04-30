package com.earmo.onlyoffice.integration.model.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentSaveStatusReq(@NotBlank(message = "documentId 不能为空。") String documentId) {
}
