package com.earmo.onlyoffice.integration.service;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;

/**
 * 远程资源安全服务契约。
 *
 * <p>接口层统一表达远程导入和图片代理共享的安全边界：
 * URI 校验、响应下载、媒体类型确认都走同一套策略，不让调用方各自散落一份校验逻辑。
 */
public interface RemoteResourceSecurityService {

    URI validateRemoteUri(String sourceUrl, String resourceLabel);

    RemoteFetchResult fetch(URI remoteUri, long maxBytes, String resourceLabel) throws IOException;

    MediaType requireImageMediaType(MediaType mediaType);

    record RemoteFetchResult(byte[] body, MediaType mediaType, String suggestedFilename) {
    }
}
