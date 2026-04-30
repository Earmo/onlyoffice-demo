package com.earmo.onlyoffice.integration.model;

public record DocumentPageReq(
        String query,
        String status,
        String sourceSystem,
        String documentType,
        String storage,
        String sortDirection,
        Integer pageNumber,
        Integer pageSize
) {
}
