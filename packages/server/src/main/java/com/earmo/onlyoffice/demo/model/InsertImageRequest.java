package com.earmo.onlyoffice.demo.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 前端请求插入网络图片时提交的参数。
 *
 * @param sourceUrl 用户输入的公网图片地址
 */
public record InsertImageRequest(@NotBlank String sourceUrl) {
}

