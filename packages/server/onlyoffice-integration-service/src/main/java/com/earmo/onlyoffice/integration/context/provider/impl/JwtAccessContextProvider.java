package com.earmo.onlyoffice.integration.context.provider.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.parser.AccessContextPermissionParser;
import com.earmo.onlyoffice.integration.context.provider.AccessContextProvider;
import com.earmo.onlyoffice.integration.exception.InvalidAccessContextException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 从 Bearer JWT 中解析访问上下文。
 *
 * <p>为了避免在 starter 里直接引入完整 Spring Security，这里只做最小 JWT 解析：
 * 从配置指定的 header 中取 Bearer Token，再按 claim-mappings 提取访问上下文字段。
 */
@Component
@RequiredArgsConstructor
public class JwtAccessContextProvider implements AccessContextProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

    @Override
    public String name() {
        return "jwt";
    }

    @Override
    public Optional<AccessContext> resolve(HttpServletRequest request) {
        if (request == null || !onlyofficeIntegrationProperties.getAccessContext().getJwt().isEnabled()) {
            return Optional.empty();
        }

        String tokenHeader = request.getHeader(onlyofficeIntegrationProperties.getAccessContext().getJwt().getHeaderName());
        if (!StringUtils.hasText(tokenHeader)) {
            return Optional.empty();
        }

        if (!tokenHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidAccessContextException("访问上下文解析失败：JWT 请求头必须使用 Bearer Token。");
        }

        String token = tokenHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new InvalidAccessContextException("访问上下文解析失败：JWT Token 不能为空。");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(resolveSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidAccessContextException("访问上下文解析失败：JWT 无法通过校验。", ex);
        }

        OnlyofficeIntegrationProperties.JwtClaimMappings mappings =
                onlyofficeIntegrationProperties.getAccessContext().getJwt().getClaimMappings();

        AccessContext accessContext = new AccessContext(
                readClaimAsString(claims, mappings.getTenantId()),
                readClaimAsString(claims, mappings.getSourceSystem()),
                readClaimAsString(claims, mappings.getExternalUserId()),
                readClaimAsString(claims, mappings.getDisplayName()),
                readClaimAsString(claims, mappings.getOrgId()),
                readClaimAsString(claims, mappings.getOrgName()),
                parsePermissions(claims.get(mappings.getPermissions())),
                name()
        );
        return accessContext.hasAnyExplicitValue() ? Optional.of(accessContext) : Optional.empty();
    }

    private SecretKey resolveSigningKey() {
        return Keys.hmacShaKeyFor(onlyofficeIntegrationProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    private String readClaimAsString(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private Map<String, Boolean> parsePermissions(Object rawPermissions) {
        if (rawPermissions == null) {
            return Map.of();
        }
        if (rawPermissions instanceof String textPermissions) {
            return AccessContextPermissionParser.parse(textPermissions);
        }
        if (rawPermissions instanceof Map<?, ?> mapPermissions) {
            Map<String, Boolean> permissions = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapPermissions.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString();
                if (!StringUtils.hasText(key)) {
                    throw new InvalidAccessContextException("访问上下文解析失败：JWT permissions 中存在空键。");
                }

                Object value = entry.getValue();
                if (value instanceof Boolean booleanValue) {
                    permissions.put(key.trim(), booleanValue);
                    continue;
                }
                if (value instanceof String textValue
                        && ("true".equalsIgnoreCase(textValue) || "false".equalsIgnoreCase(textValue))) {
                    permissions.put(key.trim(), Boolean.parseBoolean(textValue));
                    continue;
                }
                throw new InvalidAccessContextException("访问上下文解析失败：JWT permissions 值必须是布尔类型。");
            }
            return permissions;
        }
        throw new InvalidAccessContextException("访问上下文解析失败：JWT permissions 类型不受支持。");
    }
}
