package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一的接口错误响应。
 *
 * @param message 返回给前端展示的错误信息
 */
@Schema(description = "统一的接口错误响应。")
public record ApiErrorResponse(
    @Schema(description = "返回给调用方的错误提示信息。", example = "上传文件不能为空。")
    String message
) {
}



