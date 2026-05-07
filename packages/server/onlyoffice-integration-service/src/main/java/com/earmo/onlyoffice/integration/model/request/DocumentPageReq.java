package com.earmo.onlyoffice.integration.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询文档列表的请求体。
 *
 * @param query         标题或文档标识关键词。
 * @param status        文档状态筛选。
 * @param sourceSystem  来源系统筛选。
 * @param documentType  文档类型筛选。
 * @param storage       存储可用性筛选，支持 all、available、unavailable。
 * @param sortDirection 更新时间排序方向，支持 asc、desc。
 * @param pageNumber    页码，从 1 开始。
 * @param pageSize      每页条数。
 */
@Schema(description = "分页查询文档列表的请求体。")
public record DocumentPageReq(
        @Schema(description = "标题或文档标识关键词。", example = "合同")
        String query,
        @Schema(description = "文档状态筛选。", example = "active")
        String status,
        @Schema(description = "来源系统筛选。", example = "investment")
        String sourceSystem,
        @Schema(description = "文档类型筛选。", example = "word")
        String documentType,
        @Schema(description = "存储可用性筛选，支持 all、available、unavailable。", example = "all")
        String storage,
        @Schema(description = "更新时间排序方向，支持 asc、desc。", example = "desc")
        String sortDirection,
        @Schema(description = "页码，从 1 开始。", example = "1")
        Integer pageNumber,
        @Schema(description = "每页条数，最大 100。", example = "10")
        Integer pageSize
) {
}
