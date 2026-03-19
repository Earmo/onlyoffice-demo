package com.earmo.onlyoffice.demo.model;

import java.util.List;

/**
 * 文档列表响应。
 */
public record DocumentListResponse(List<DocumentSummaryResponse> documents) {
}
