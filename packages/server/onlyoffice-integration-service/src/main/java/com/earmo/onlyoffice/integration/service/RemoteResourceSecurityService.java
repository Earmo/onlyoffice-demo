package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 统一收口远程资源访问的安全边界。
 *
 * <p>Phase 5 开始，远程导入和图片代理不再各自维护一套“只拦 localhost”的轻量校验，
 * 而是共用同一套 SSRF、防大包和基础响应约束。
 */
@Service
@RequiredArgsConstructor
public class RemoteResourceSecurityService {

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  private final RestClient.Builder restClientBuilder;

  public URI validateRemoteUri(String sourceUrl, String resourceLabel) {
    if (!StringUtils.hasText(sourceUrl)) {
      throw new IllegalArgumentException(resourceLabel + "不能为空。");
    }

    URI uri = URI.create(sourceUrl.trim());
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
      throw new IllegalArgumentException(resourceLabel + "必须是完整的 http/https URL。");
    }

    String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
    if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
      throw new IllegalArgumentException("当前只支持访问 http/https 远程资源。");
    }

    if (!onlyofficeIntegrationProperties.getRemoteResource().isAllowPrivateAddressAccess()) {
      validateResolvedAddress(host, resourceLabel);
    }
    return uri;
  }

  public RemoteFetchResult fetch(URI remoteUri, long maxBytes, String resourceLabel) throws IOException {
    return restClientBuilder.build()
        .get()
        .uri(remoteUri)
        .exchange((request, response) -> {
          long contentLength = response.getHeaders().getContentLength();
          if (contentLength > maxBytes) {
            throw new IllegalArgumentException(
                resourceLabel + "响应超过大小限制，当前最大允许 " + formatLimit(maxBytes) + "。"
            );
          }

          try (InputStream bodyStream = response.getBody()) {
            byte[] body = readBodyWithLimit(bodyStream, maxBytes, resourceLabel);
            return new RemoteFetchResult(body, response.getHeaders().getContentType());
          }
        });
  }

  public MediaType requireImageMediaType(MediaType mediaType) {
    if (mediaType == null) {
      throw new IllegalArgumentException("远程图片响应缺少 Content-Type，无法确认是否为图片资源。");
    }
    if (!"image".equalsIgnoreCase(mediaType.getType())) {
      throw new IllegalArgumentException("远程图片响应不是合法图片类型，实际为：" + mediaType + "。");
    }
    return mediaType;
  }

  private void validateResolvedAddress(String host, String resourceLabel) {
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) {
        throw new IllegalArgumentException(resourceLabel + "无法解析到可访问地址。");
      }

      for (InetAddress address : addresses) {
        if (isBlockedAddress(address)) {
          throw new IllegalArgumentException(resourceLabel + "不支持访问内网、回环或保留地址。");
        }
      }
    } catch (UnknownHostException ex) {
      throw new IllegalArgumentException(resourceLabel + "主机名无法解析。", ex);
    }
  }

  private boolean isBlockedAddress(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isUniqueLocalIpv6(address);
  }

  private boolean isUniqueLocalIpv6(InetAddress address) {
    if (!(address instanceof Inet6Address inet6Address)) {
      return false;
    }
    byte[] bytes = inet6Address.getAddress();
    return bytes.length == 16 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
  }

  private byte[] readBodyWithLimit(InputStream bodyStream, long maxBytes, String resourceLabel) throws IOException {
    if (bodyStream == null) {
      throw new IOException(resourceLabel + "下载失败，响应内容为空。");
    }

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long totalBytes = 0;
      int readSize;
      while ((readSize = bodyStream.read(buffer)) != -1) {
        totalBytes += readSize;
        if (totalBytes > maxBytes) {
          throw new IllegalArgumentException(
              resourceLabel + "响应超过大小限制，当前最大允许 " + formatLimit(maxBytes) + "。"
          );
        }
        outputStream.write(buffer, 0, readSize);
      }

      if (totalBytes == 0) {
        throw new IOException(resourceLabel + "下载失败，响应内容为空。");
      }
      return outputStream.toByteArray();
    }
  }

  private String formatLimit(long maxBytes) {
    long megaBytes = maxBytes / (1024 * 1024);
    return megaBytes > 0 ? megaBytes + "MB" : maxBytes + " 字节";
  }

  public record RemoteFetchResult(byte[] body, MediaType mediaType) {
  }
}
