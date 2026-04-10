package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeCommandService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ONLYOFFICE Command Service API 默认实现。
 *
 * <p>通过 HTTP POST 向 ONLYOFFICE Document Server 的
 * {@code /coauthoring/CommandService.ashx} 端点发送命令。
 * 请求体包含 JWT 签名的 JSON payload，Document Server 验签后执行命令。
 *
 * <p>{@link #forceSaveAndAwait} 利用 {@link CompletableFuture} 在发送命令后阻塞
 * 当前线程，等待 callback 端点调用 {@link #notifySaveCompleted} 释放锁，
 * 从而保证"保存并返回"时文件已真正落盘。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyofficeCommandServiceImpl implements OnlyofficeCommandService {

  private final OnlyofficeIntegrationProperties properties;
  private final DocumentStorageService documentStorageService;
  private final OnlyofficeJwtService jwtService;
  private final RestClient.Builder restClientBuilder;

  /** 正在等待 callback 回写完成的 forceSave 请求。 */
  private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();

  @Override
  public void forceSave(String documentId) {
    invokeForceSave(documentId);
  }

  @Override
  public boolean forceSaveAndAwait(String documentId, long timeoutMillis) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    pendingSaves.put(documentId, future);
    StoredDocument storedDocument;
    try {
      Integer errorCode = invokeForceSave(documentId);
      if (Integer.valueOf(4).equals(errorCode)) {
        log.debug("forceSaveAndAwait 命中无待保存修改，直接返回成功：documentId={}", documentId);
        return true;
      }
      if (errorCode != null && !Integer.valueOf(0).equals(errorCode)) {
        log.warn("forceSaveAndAwait 命令返回非零错误码：error={}, documentId={}", errorCode, documentId);
        return false;
      }
      future.get(timeoutMillis, TimeUnit.MILLISECONDS);
      log.debug("forceSaveAndAwait 成功等到 callback 回写完成：documentId={}", documentId);
      return true;
    } catch (Exception ex) {
      log.warn("forceSaveAndAwait 等待超时或失败，documentId={}：{}", documentId, ex.getMessage());
      return false;
    } finally {
      pendingSaves.remove(documentId);
    }
  }

  @Override
  public void notifySaveCompleted(String documentId) {
    CompletableFuture<Void> future = pendingSaves.get(documentId);
    if (future != null) {
      future.complete(null);
    }
  }

  private Integer invokeForceSave(String documentId) {
    StoredDocument storedDocument;
    try {
      storedDocument = documentStorageService.getRequiredDocument(documentId);
    } catch (Exception ex) {
      log.warn("获取文档信息失败，跳过 forcesave，documentId={}：{}", documentId, ex.getMessage());
      return null;
    }
    String documentKey = OnlyofficeDocumentKeyResolver.resolveDocumentKey(storedDocument);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("c", "forcesave");
    payload.put("key", documentKey);
    payload.put("token", jwtService.sign(payload));

    String commandUrl = resolveCommandServiceUrl();

    log.debug("向 ONLYOFFICE Document Server 发送 forcesave 命令：documentId={}, key={}, url={}", documentId, documentKey, commandUrl);

    try {
      Map<?, ?> response = restClientBuilder.build()
          .post()
          .uri(commandUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(Map.class);

      Integer errorCode = response != null && response.get("error") instanceof Number number
          ? number.intValue()
          : Integer.valueOf(0);
      if (Integer.valueOf(4).equals(errorCode)) {
        log.debug("forcesave 返回 error=4（无待保存修改），documentId={}", documentId);
      } else if (!Integer.valueOf(0).equals(errorCode)) {
        log.warn("forcesave 命令返回非零错误码：error={}, documentId={}", errorCode, documentId);
      } else {
        log.debug("forcesave 命令执行成功：documentId={}", documentId);
      }
      return errorCode;
    } catch (Exception ex) {
      log.warn("forcesave 命令调用失败，documentId={}：{}", documentId, ex.getMessage());
      return null;
    }
  }

  private String resolveCommandServiceUrl() {
    String commandBaseUrl = properties.getDocumentServerCommandUrl();
    if (commandBaseUrl != null && !commandBaseUrl.isBlank()) {
      String trimmed = commandBaseUrl.endsWith("/") ? commandBaseUrl.substring(0, commandBaseUrl.length() - 1) : commandBaseUrl;
      return trimmed + "/coauthoring/CommandService.ashx";
    }
    // 优先使用 document-server-url（浏览器/宿主机 → DS 的地址，本地开发时后端也在宿主机上，可直接复用）。
    String dsUrl = properties.getDocumentServerUrl();
    if (dsUrl != null && !dsUrl.isBlank()) {
      String trimmed = dsUrl.endsWith("/") ? dsUrl.substring(0, dsUrl.length() - 1) : dsUrl;
      return trimmed + "/coauthoring/CommandService.ashx";
    }
    // Docker 全容器部署时 document-server-url 通常为空（前端同源代理），
    // 回退到 internal-base-url（指向 nginx），nginx 会把 /coauthoring/ 转发给 DS。
    String baseUrl = properties.getInternalBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/coauthoring/CommandService.ashx";
  }
}
