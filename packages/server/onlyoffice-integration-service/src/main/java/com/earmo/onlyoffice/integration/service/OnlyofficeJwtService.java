package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 统一负责 ONLYOFFICE 相关 JWT 签名。
 *
 * <p>这样编辑器初始化配置、insertImage 调用参数等都能复用同一套签名逻辑。
 */
@Service
@RequiredArgsConstructor
public class OnlyofficeJwtService {

  private static final String BEARER_PREFIX = "Bearer ";

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

  public String sign(Map<String, Object> payload) {
    return Jwts.builder()
        .claims(payload)
        .signWith(resolveSigningKey())
        .compact();
  }

  /**
   * 校验 ONLYOFFICE callback 请求头里的 JWT。
   *
   * <p>这里显式沿用 starter 当前配置的共享 secret，把 callback 可信性建立在应用层签名上，
   * 而不是依赖容器网络拓扑“恰好只有 ONLYOFFICE 能打到接口”。
   */
  public Claims verifyCallbackRequest(HttpServletRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("ONLYOFFICE callback JWT 校验失败：请求上下文不能为空。");
    }

    String headerName = onlyofficeIntegrationProperties.getCallback().getJwtHeaderName();
    String headerValue = request.getHeader(headerName);
    if (!StringUtils.hasText(headerValue)) {
      throw new IllegalArgumentException(
          "ONLYOFFICE callback JWT 校验失败：缺少请求头 " + headerName + "。"
      );
    }
    return verify(extractToken(headerValue));
  }

  public Claims verify(String token) {
    if (!StringUtils.hasText(token)) {
      throw new IllegalArgumentException("ONLYOFFICE callback JWT 校验失败：Token 不能为空。");
    }

    try {
      return Jwts.parser()
          .verifyWith(resolveSigningKey())
          .build()
          .parseSignedClaims(token.trim())
          .getPayload();
    } catch (JwtException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("ONLYOFFICE callback JWT 校验失败：签名无效。", ex);
    }
  }

  private SecretKey resolveSigningKey() {
    return Keys.hmacShaKeyFor(
        onlyofficeIntegrationProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8)
    );
  }

  private String extractToken(String headerValue) {
    String trimmedHeaderValue = headerValue.trim();
    if (!StringUtils.hasText(trimmedHeaderValue)) {
      throw new IllegalArgumentException("ONLYOFFICE callback JWT 校验失败：Token 不能为空。");
    }

    if (trimmedHeaderValue.startsWith(BEARER_PREFIX)) {
      String bearerToken = trimmedHeaderValue.substring(BEARER_PREFIX.length()).trim();
      if (!StringUtils.hasText(bearerToken)) {
        throw new IllegalArgumentException("ONLYOFFICE callback JWT 校验失败：Bearer Token 不能为空。");
      }
      return bearerToken;
    }

    return trimmedHeaderValue;
  }
}



