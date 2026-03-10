package com.earmo.onlyoffice.demo.model;

import org.springframework.http.MediaType;

/**
 * 远程图片代理结果。
 *
 * @param body 图片字节内容
 * @param mediaType 图片媒体类型
 * @param filename 返回给浏览器或 ONLYOFFICE 的文件名
 */
public record RemoteImageResource(byte[] body, MediaType mediaType, String filename) {
}

