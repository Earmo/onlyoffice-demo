package com.earmo.onlyoffice.demo.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 导入网络文档时的请求体。
 *
 * @param sourceUrl 可公开访问的文档 URL
 */
public record DocumentImportRequest(@NotBlank String sourceUrl) {
}

