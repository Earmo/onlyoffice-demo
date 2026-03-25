package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.InsertImageResponse;
import com.earmo.onlyoffice.integration.model.RemoteImageResource;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 负责网络图片插入相关逻辑。
 *
 * <p>宿主页面不能持有 ONLYOFFICE 的 JWT 密钥，因此由后端生成
 * docEditor.insertImage(...) 所需的签名参数，并在需要时代理下载远程图片。
 */
@Service
@RequiredArgsConstructor
public class OnlyofficeImageService {

  private static final Set<String> SUPPORTED_FILE_TYPES = Set.of(
      "bmp", "gif", "jpg", "jpeg", "png", "svg", "tif", "tiff", "webp"
  );

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  private final OnlyofficeJwtService onlyofficeJwtService;
  private final RemoteResourceSecurityService remoteResourceSecurityService;

  /**
   * 构造 ONLYOFFICE insertImage 方法所需的参数。
   *
   * <p>图片 URL 不直接暴露给编辑器，而是先走当前后端的代理接口，
   * 这样既能统一验参，也能保证签名对象完全由服务端控制。
   */
  public InsertImageResponse buildInsertImageResponse(String documentId, String sourceUrl) {
    URI remoteUri = parseAndValidateSourceUrl(sourceUrl);
    String fileType = resolveFileType(remoteUri);
    String proxiedUrl = buildInternalImageProxyUrl(documentId, remoteUri.toString());

    Map<String, Object> insertImage = new LinkedHashMap<>();
    insertImage.put("c", "add");
    insertImage.put("fileType", fileType);
    insertImage.put("url", proxiedUrl);
    insertImage.put("token", onlyofficeJwtService.sign(insertImage));

    return new InsertImageResponse(insertImage);
  }

  /**
   * 代理下载远程图片，供 ONLYOFFICE Docs 拉取实际图片内容。
   */
  public RemoteImageResource proxyRemoteImage(String sourceUrl) throws IOException {
    URI remoteUri = parseAndValidateSourceUrl(sourceUrl);
    RemoteResourceSecurityService.RemoteFetchResult remoteFetchResult = remoteResourceSecurityService.fetch(
        remoteUri,
        onlyofficeIntegrationProperties.getRemoteResource().getMaxImageBytes(),
        "远程图片"
    );

    String filename = extractFilename(remoteUri);
    MediaType mediaType = remoteResourceSecurityService.requireImageMediaType(remoteFetchResult.mediaType());

    return new RemoteImageResource(remoteFetchResult.body(), mediaType, filename);
  }

  private URI parseAndValidateSourceUrl(String sourceUrl) {
    return remoteResourceSecurityService.validateRemoteUri(sourceUrl, "图片地址");
  }

  private String buildInternalImageProxyUrl(String documentId, String sourceUrl) {
    return UriComponentsBuilder.fromHttpUrl(onlyofficeIntegrationProperties.getInternalBaseUrl())
        .path("/api/documents/{documentId}/images/proxy")
        .queryParam("sourceUrl", sourceUrl)
        .buildAndExpand(documentId)
        .toUriString();
  }

  private String resolveFileType(URI remoteUri) {
    String path = remoteUri.getPath();
    if (!StringUtils.hasText(path)) {
      throw new IllegalArgumentException("图片地址缺少文件扩展名，无法识别图片类型。");
    }

    String filename = Paths.get(path).getFileName().toString();
    int index = filename.lastIndexOf('.');
    if (index < 0 || index == filename.length() - 1) {
      throw new IllegalArgumentException("图片地址缺少文件扩展名，当前示例无法识别图片类型。");
    }

    String extension = filename.substring(index + 1).toLowerCase(Locale.ROOT);
    if (!SUPPORTED_FILE_TYPES.contains(extension)) {
      throw new IllegalArgumentException("暂不支持该图片类型：" + extension);
    }

    return switch (extension) {
      case "jpeg" -> "jpg";
      case "tiff" -> "tif";
      default -> extension;
    };
  }

  private String extractFilename(URI remoteUri) {
    String path = remoteUri.getPath();
    if (!StringUtils.hasText(path)) {
      return "remote-image";
    }

    String filename = Paths.get(path).getFileName().toString();
    return StringUtils.hasText(filename) ? filename : "remote-image";
  }
}


