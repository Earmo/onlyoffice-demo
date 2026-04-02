package com.earmo.onlyoffice.integration.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * ONLYOFFICE JWT 服务契约。
 */
public interface OnlyofficeJwtService {

  String sign(Map<String, Object> payload);

  Claims verifyCallbackRequest(HttpServletRequest request);

  Claims verify(String token);
}
