package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.service.RemoteResourceSecurityService;
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
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 远程资源安全服务默认实现。
 *
 * <p>这个实现统一收口三件事：
 * 1. SSRF 相关的主机、协议和私网访问限制；
 * 2. 远程响应体大小限制，避免大文件把服务内存拖垮；
 * 3. 图片代理与远程导入共享的基础内容类型校验。
 *
 * <p>这样 DocumentStorageServiceImpl 和 OnlyofficeImageServiceImpl 只负责业务编排，
 * 不需要再各自维护一份下载安全边界。
 */
@Service
@RequiredArgsConstructor
public class RemoteResourceSecurityServiceImpl implements RemoteResourceSecurityService {

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  private final RestClient.Builder restClientBuilder;

  /**
   * 校验远程资源 URL 是否允许访问。
   *
   * @param sourceUrl 原始远程资源 URL。
   * @param resourceLabel 错误提示中展示的资源名称。
   * @return 通过校验的 URI。
   */
  @Override
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

  /**
   * 下载远程资源并限制响应体大小。
   *
   * @param remoteUri 已校验的远程资源 URI。
   * @param maxBytes 最大允许字节数。
   * @param resourceLabel 错误提示中展示的资源名称。
   * @return 下载结果。
   * @throws IOException 响应体读取失败时抛出。
   */
  @Override
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
            return new RemoteFetchResult(
                body,
                response.getHeaders().getContentType(),
                resolveSuggestedFilename(response.getHeaders().getFirst("Content-Disposition"))
            );
          }
        });
  }

  /**
   * 校验远程响应是否为图片媒体类型。
   *
   * @param mediaType 远程响应 Content-Type。
   * @return 原始媒体类型。
   */
  @Override
  public MediaType requireImageMediaType(MediaType mediaType) {
    if (mediaType == null) {
      throw new IllegalArgumentException("远程图片响应缺少 Content-Type，无法确认是否为图片资源。");
    }
    if (!"image".equalsIgnoreCase(mediaType.getType())) {
      throw new IllegalArgumentException("远程图片响应不是合法图片类型，实际为：" + mediaType + "。");
    }
    return mediaType;
  }

  /**
   * 解析主机名后逐个检查真实地址，避免只看原始字符串造成绕过。
   *
   * <p>例如调用方即使传入一个看起来正常的域名，只要它最终解析到回环、私网或保留地址，
   * 这里都会拒绝，防止服务被利用去探测内网资源。
   *
   * @param host 待解析主机名。
   * @param resourceLabel 错误提示中展示的资源名称。
   */
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

  /**
   * 判断 IP 地址是否属于禁止访问的范围。
   *
   * @param address 已解析 IP 地址。
   * @return true 表示该地址不允许远程访问。
   */
  private boolean isBlockedAddress(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isUniqueLocalIpv6(address);
  }

  /**
   * 判断 IPv6 地址是否属于 unique local address 范围。
   *
   * @param address 已解析 IP 地址。
   * @return true 表示该地址是 fc00::/7 范围内的 ULA。
   */
  private boolean isUniqueLocalIpv6(InetAddress address) {
    if (!(address instanceof Inet6Address inet6Address)) {
      return false;
    }
    byte[] bytes = inet6Address.getAddress();
    return bytes.length == 16 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
  }

  /**
   * 分块读取远程响应体，并在读取过程中实时限制累计大小。
   *
   * <p>这里刻意不用 `readAllBytes()`，因为安全边界要求我们在内容还没完全进内存前就能终止超大响应，
   * 否则“先读完再判断大小”会让限制形同虚设。
   *
   * @param bodyStream 远程响应体输入流。
   * @param maxBytes 最大允许字节数。
   * @param resourceLabel 错误提示中展示的资源名称。
   * @return 响应体字节数组。
   * @throws IOException 响应体为空或读取失败时抛出。
   */
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

  /**
   * 将字节数格式化成面向用户的限制文案。
   *
   * @param maxBytes 最大允许字节数。
   * @return 格式化后的限制文案。
   */
  private String formatLimit(long maxBytes) {
    long megaBytes = maxBytes / (1024 * 1024);
    return megaBytes > 0 ? megaBytes + "MB" : maxBytes + " 字节";
  }

  /**
   * 从 Content-Disposition 中解析建议文件名。
   *
   * @param rawContentDisposition 原始 Content-Disposition 头。
   * @return 建议文件名；无法解析时返回 null。
   */
  private String resolveSuggestedFilename(String rawContentDisposition) {
    if (!StringUtils.hasText(rawContentDisposition)) {
      return null;
    }

    try {
      ContentDisposition disposition = ContentDisposition.parse(rawContentDisposition);
      String filename = disposition.getFilename();
      return StringUtils.hasText(filename) ? filename : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
