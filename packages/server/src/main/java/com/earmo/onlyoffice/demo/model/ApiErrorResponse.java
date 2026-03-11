package com.earmo.onlyoffice.demo.model;

/**
 * 统一的接口错误响应。
 *
 * @param message 返回给前端展示的错误信息
 */
public record ApiErrorResponse(String message) {
}

