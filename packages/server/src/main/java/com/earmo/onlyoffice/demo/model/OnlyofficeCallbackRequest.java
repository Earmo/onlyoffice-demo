package com.earmo.onlyoffice.demo.model;

/**
 * ONLYOFFICE callback 的最小请求体。
 *
 * @param status 文档当前状态码，示例里只关心 2 和 6
 * @param url 当文档可持久化时，ONLYOFFICE 提供的最新文件下载地址
 */
public record OnlyofficeCallbackRequest(Integer status, String url) {
}
