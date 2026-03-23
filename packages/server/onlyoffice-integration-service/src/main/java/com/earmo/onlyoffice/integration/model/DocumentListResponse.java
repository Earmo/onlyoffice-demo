package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 文档列表响应。
 */
@Schema(description = "文档列表接口响应。")
public record DocumentListResponse(
    @Schema(description = "当前租户下可见的文档摘要列表。")
    List<DocumentSummaryResponse> documents
) {
}


