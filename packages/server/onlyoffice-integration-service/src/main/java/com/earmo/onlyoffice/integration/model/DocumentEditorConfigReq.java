package com.earmo.onlyoffice.integration.model;

import jakarta.validation.constraints.NotBlank;

public record DocumentEditorConfigReq(
    @NotBlank(message = "documentId 不能为空。") String documentId,
    Boolean readonly
) {
}
