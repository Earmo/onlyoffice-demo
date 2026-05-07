package com.earmo.onlyoffice.integration.context.parser;

import com.earmo.onlyoffice.integration.exception.InvalidAccessContextException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析最小权限映射。
 *
 * <p>当前约定输入格式为 `edit=true,comment=false,download=true,print=false`。
 * 之所以不直接上更重的权限 DSL，是因为 Phase 3 只需要把少量和 editor config 直接相关的能力透传进来，
 * 先把语义跑通，再为后续扩展保留空间。
 */
public final class AccessContextPermissionParser {

    private AccessContextPermissionParser() {
    }

    public static Map<String, Boolean> parse(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Map.of();
        }

        Map<String, Boolean> permissions = new LinkedHashMap<>();
        String[] entries = rawValue.split(",");
        for (String entry : entries) {
            if (!StringUtils.hasText(entry) || !entry.contains("=")) {
                throw new InvalidAccessContextException("访问上下文解析失败：权限配置格式不合法。");
            }

            String[] pair = entry.split("=", 2);
            String key = pair[0].trim();
            String value = pair[1].trim();
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                throw new InvalidAccessContextException("访问上下文解析失败：权限配置格式不合法。");
            }

            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new InvalidAccessContextException("访问上下文解析失败：权限值必须是 true 或 false。");
            }
            permissions.put(key, Boolean.parseBoolean(value));
        }
        return permissions;
    }
}
