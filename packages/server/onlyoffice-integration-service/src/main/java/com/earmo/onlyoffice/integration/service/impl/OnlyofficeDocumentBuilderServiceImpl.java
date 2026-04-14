package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.service.OnlyofficeDocumentBuilderService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ONLYOFFICE Document Builder HTTP API 默认实现。
 *
 * <p>通过 HTTP POST 向 ONLYOFFICE Document Server 的 {@code /docbuilder} 端点提交脚本 URL，
 * Document Server 拉取并执行该 JavaScript 脚本（使用与编辑器内宏相同的 Office API），
 * 返回处理后文档的临时下载地址。
 *
 * <p>Document Builder URL 解析优先级（同 Conversion API）：
 * <ol>
 *   <li>{@code documentServerCommandUrl}（去尾斜杠）+ {@code /docbuilder}</li>
 *   <li>{@code documentServerUrl}（去尾斜杠）+ {@code /docbuilder}</li>
 *   <li>{@code internalBaseUrl}（去尾斜杠）+ {@code /docbuilder}（兜底）</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyofficeDocumentBuilderServiceImpl implements OnlyofficeDocumentBuilderService {

  private final OnlyofficeIntegrationProperties properties;
  private final OnlyofficeJwtService jwtService;
  private final RestClient.Builder restClientBuilder;

  @Override
  public byte[] runScript(String scriptUrl, String outputFileName) throws IOException {
    String builderUrl = resolveDocumentBuilderUrl();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("async", false);
    payload.put("url", scriptUrl);
    payload.put("token", jwtService.sign(payload));

    log.debug("调用 ONLYOFFICE Document Builder API：builderUrl={}, scriptUrl={}", builderUrl, scriptUrl);

    Map<?, ?> response = restClientBuilder.build()
        .post()
        .uri(builderUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .body(Map.class);

    if (response == null) {
      throw new IllegalStateException("ONLYOFFICE Document Builder API 返回空响应");
    }

    Object error = response.get("error");
    if (error instanceof Number num && num.intValue() != 0) {
      throw new IllegalStateException("ONLYOFFICE Document Builder API 返回错误码：" + num.intValue());
    }

    Boolean end = response.get("end") instanceof Boolean b ? b : false;
    if (!Boolean.TRUE.equals(end)) {
      throw new IllegalStateException("ONLYOFFICE Document Builder API 脚本执行未完成（end=false）");
    }

    @SuppressWarnings("unchecked")
    Map<String, String> urls = (Map<String, String>) response.get("urls");
    if (urls == null || !urls.containsKey(outputFileName)) {
      throw new IllegalStateException(
          "ONLYOFFICE Document Builder API 响应中缺少结果文件 URL，outputFileName=" + outputFileName);
    }

    String resultUrl = urls.get(outputFileName);
    log.debug("Builder 脚本执行完成，开始下载结果文件：resultUrl={}", resultUrl);

    byte[] resultBytes = restClientBuilder.build()
        .get()
        .uri(resultUrl)
        .retrieve()
        .body(byte[].class);

    if (resultBytes == null || resultBytes.length == 0) {
      throw new IOException("下载 Document Builder 结果文件失败：resultUrl=" + resultUrl);
    }

    log.debug("Builder 结果文件下载完成：size={}bytes", resultBytes.length);
    return resultBytes;
  }

  @Override
  public String generateRemoveWatermarkScript(String documentId, String fileType) {
    String baseUrl = properties.getInternalBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    String documentFileUrl = baseUrl + "/api/documents/" + documentId + "/file." + fileType;
    String outputFileName = "output." + fileType;

    return """
        builder.OpenFile("%s", "");
        var oDocument = Api.GetDocument();
        oDocument.RemoveWatermark();
        builder.SaveFile("%s", "%s");
        builder.CloseFile();
        """.formatted(documentFileUrl, fileType, outputFileName);
  }

  /**
   * 解析 ONLYOFFICE Document Builder API URL。
   * 优先级：commandUrl → documentServerUrl → internalBaseUrl（兜底）
   */
  private String resolveDocumentBuilderUrl() {
    String commandBaseUrl = properties.getDocumentServerCommandUrl();
    if (commandBaseUrl != null && !commandBaseUrl.isBlank()) {
      String trimmed = commandBaseUrl.endsWith("/")
          ? commandBaseUrl.substring(0, commandBaseUrl.length() - 1)
          : commandBaseUrl;
      return trimmed + "/docbuilder";
    }
    String dsUrl = properties.getDocumentServerUrl();
    if (dsUrl != null && !dsUrl.isBlank()) {
      String trimmed = dsUrl.endsWith("/") ? dsUrl.substring(0, dsUrl.length() - 1) : dsUrl;
      return trimmed + "/docbuilder";
    }
    String baseUrl = properties.getInternalBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/docbuilder";
  }
}
