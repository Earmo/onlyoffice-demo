package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 前端请求插入网络图片时提交的参数。
 *
 * @param sourceUrl 用户输入的公网图片地址
 */
@Schema(description = "插入网络图片请求体。")
public record InsertImageRequest(
        @NotBlank
        @Schema(description = "用户输入的公网图片地址。", example = "https://example.com/logo.png")
        String sourceUrl
) {
}



