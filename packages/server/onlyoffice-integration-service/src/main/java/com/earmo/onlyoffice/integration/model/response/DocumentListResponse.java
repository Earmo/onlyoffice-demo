package com.earmo.onlyoffice.integration.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 文档列表响应。
 */
@Schema(description = "文档列表接口响应。")
public record DocumentListResponse(
        @Schema(description = "当前请求解析出的租户 ID。", example = "tenant-a")
        String tenantId,
        @Schema(description = "当前请求解析出的操作者标识。", example = "user-a")
        String actorUser,
        @Schema(description = "当前请求解析出的操作者展示名。", example = "Alice")
        String actorName,
        @Schema(description = "当前返回页码，从 1 开始。", example = "1")
        int pageNumber,
        @Schema(description = "当前每页条数。", example = "10")
        int pageSize,
        @Schema(description = "符合当前筛选条件的总文档数。", example = "42")
        long total,
        @Schema(description = "符合当前筛选条件的总页数。", example = "5")
        long totalPages,
        @Schema(description = "当前租户下可见的文档摘要列表。")
        List<DocumentSummaryResponse> documents
) {
}


