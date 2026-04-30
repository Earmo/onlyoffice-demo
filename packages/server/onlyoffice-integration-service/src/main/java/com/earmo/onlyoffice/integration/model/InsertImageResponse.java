package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 返回给前端的 ONLYOFFICE insertImage 调用参数。
 *
 * @param insertImage 可直接传给 docEditor.insertImage(...) 的对象
 */
@Schema(description = "ONLYOFFICE insertImage 响应参数。")
public record InsertImageResponse(
        @Schema(description = "可直接传给 docEditor.insertImage(...) 的配置对象。")
        Map<String, Object> insertImage
) {
}



