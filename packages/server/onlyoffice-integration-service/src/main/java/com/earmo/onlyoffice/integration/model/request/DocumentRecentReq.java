package com.earmo.onlyoffice.integration.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 查询最近编辑文档的请求体。
 *
 * @param limit 返回数量限制。
 */
@Schema(description = "查询最近编辑文档的请求体。")
public record DocumentRecentReq(
        @Schema(description = "返回数量限制，最大 10。", example = "3")
        Integer limit
) {
}
