package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.service.OnlyofficeConversionService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ONLYOFFICE Conversion API 默认实现。
 *
 * <p>通过 HTTP POST 向 ONLYOFFICE Document Server 的 {@code /converter} 端点发送转换请求，
 * 同步等待转换完成（{@code async: false}），再从结果 URL 下载转换后的文件字节。
 *
 * <p>Conversion URL 解析优先级：
 * <ol>
 *   <li>{@code documentServerCommandUrl}（去尾斜杠）+ {@code /converter}</li>
 *   <li>{@code documentServerUrl}（去尾斜杠）+ {@code /converter}</li>
 *   <li>{@code internalBaseUrl}（去尾斜杠）+ {@code /converter}（兜底）</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyofficeConversionServiceImpl implements OnlyofficeConversionService {

  private final OnlyofficeIntegrationProperties properties;
  private final OnlyofficeJwtService jwtService;
  private final RestClient.Builder restClientBuilder;

  @Override
  public byte[] convertDocument(String documentId, String sourceFileType, String outputFileType)
      throws IOException {
    String converterUrl = resolveConverterUrl();
    String downloadUrl = resolveDocumentFileUrl(documentId, sourceFileType);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("async", false);
    payload.put("filetype", sourceFileType);
    payload.put("key", UUID.randomUUID().toString().replace("-", ""));
    payload.put("outputtype", outputFileType);
    payload.put("title", documentId + "." + outputFileType);
    payload.put("url", downloadUrl);
    payload.put("token", jwtService.sign(payload));

    log.debug("调用 ONLYOFFICE Conversion API：url={}, filetype={}, outputtype={}", converterUrl, sourceFileType, outputFileType);

    Map<?, ?> response = restClientBuilder.build()
        .post()
        .uri(converterUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .body(Map.class);

    if (response == null) {
      throw new IllegalStateException("ONLYOFFICE Conversion API 返回空响应");
    }

    Object error = response.get("error");
    if (error instanceof Number num && num.intValue() != 0) {
      throw new IllegalStateException("ONLYOFFICE Conversion API 返回错误码：" + num.intValue());
    }

    Boolean endConvert = response.get("endConvert") instanceof Boolean b ? b : false;
    if (!Boolean.TRUE.equals(endConvert)) {
      throw new IllegalStateException("ONLYOFFICE Conversion API 转换尚未完成（endConvert=false），请检查文档 URL 是否可访问");
    }

    String fileUrl = (String) response.get("fileUrl");
    if (fileUrl == null || fileUrl.isBlank()) {
      throw new IllegalStateException("ONLYOFFICE Conversion API 响应中缺少 fileUrl");
    }

    log.debug("转换完成，开始下载结果文件：fileUrl={}", fileUrl);

    byte[] resultBytes = restClientBuilder.build()
        .get()
        .uri(fileUrl)
        .retrieve()
        .body(byte[].class);

    if (resultBytes == null || resultBytes.length == 0) {
      throw new IOException("下载转换结果文件失败：fileUrl=" + fileUrl);
    }

    log.debug("转换结果下载完成：size={}bytes", resultBytes.length);
    return resultBytes;
  }

  /**
   * 解析 ONLYOFFICE Conversion API URL。
   * 优先级：commandUrl → documentServerUrl → internalBaseUrl（兜底）
   */
  private String resolveConverterUrl() {
    String commandBaseUrl = properties.getDocumentServerCommandUrl();
    if (commandBaseUrl != null && !commandBaseUrl.isBlank()) {
      String trimmed = commandBaseUrl.endsWith("/")
          ? commandBaseUrl.substring(0, commandBaseUrl.length() - 1)
          : commandBaseUrl;
      return trimmed + "/converter";
    }
    String dsUrl = properties.getDocumentServerUrl();
    if (dsUrl != null && !dsUrl.isBlank()) {
      String trimmed = dsUrl.endsWith("/") ? dsUrl.substring(0, dsUrl.length() - 1) : dsUrl;
      return trimmed + "/converter";
    }
    String baseUrl = properties.getInternalBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/converter";
  }

  /**
   * 构造 ONLYOFFICE Document Server 可访问的文档文件下载 URL。
   * 必须使用 internalBaseUrl（容器内部地址），不能用 documentServerUrl（浏览器地址）。
   */
  private String resolveDocumentFileUrl(String documentId, String fileType) {
    String baseUrl = properties.getInternalBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/api/documents/" + documentId + "/file." + fileType;
  }
}
