package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一负责 ONLYOFFICE 相关 JWT 签名。
 *
 * <p>这样编辑器初始化配置、insertImage 调用参数等都能复用同一套签名逻辑。
 */
@Service
@RequiredArgsConstructor
public class OnlyofficeJwtService {

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

  public String sign(Map<String, Object> payload) {
    SecretKey key = Keys.hmacShaKeyFor(
        onlyofficeIntegrationProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8)
    );

    return Jwts.builder()
        .claims(payload)
        .signWith(key)
        .compact();
  }
}



