package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * 统一负责 ONLYOFFICE 相关 JWT 签名。
 *
 * <p>这样编辑器初始化配置、insertImage 调用参数等都能复用同一套签名逻辑。
 */
@Service
public class OnlyofficeJwtService {

  private final DemoProperties demoProperties;

  public OnlyofficeJwtService(DemoProperties demoProperties) {
    this.demoProperties = demoProperties;
  }

  public String sign(Map<String, Object> payload) {
    SecretKey key = Keys.hmacShaKeyFor(
        demoProperties.getOnlyoffice().getJwtSecret().getBytes(StandardCharsets.UTF_8)
    );

    return Jwts.builder()
        .claims(payload)
        .signWith(key)
        .compact();
  }
}

