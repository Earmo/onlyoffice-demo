package com.earmo.onlyoffice.integration.model;

import jakarta.validation.constraints.NotBlank;

public record DocumentSaveReq(@NotBlank(message = "documentId 不能为空。") String documentId) {
}
