package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.model.RequestContext;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一表达“当前访问者是谁、来自哪里、具备哪些最小编辑能力”。
 *
 * <p>Phase 3 之后，controller 与运行时协议层不再直接围绕请求头工作，而是统一消费这个模型。
 * 这样无论上游系统通过 header、JWT 还是自定义 provider 透传用户信息，后续业务调用链都只需要面对
 * 同一套访问上下文，而不需要知道身份数据的真实来源。
 */
public record AccessContext(
        String tenantId,
        String sourceSystem,
        String externalUserId,
        String displayName,
        Map<String, Boolean> permissions,
        String source
) {

    public AccessContext {
        permissions = permissions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(permissions));
    }

    /**
     * 判断 provider 是否至少解析出了部分显式上下文。
     */
    public boolean hasAnyExplicitValue() {
        return hasText(tenantId)
                || hasText(sourceSystem)
                || hasText(externalUserId)
                || hasText(displayName)
                || !permissions.isEmpty();
    }

    /**
     * 判断访问上下文是否已经满足主业务链路最小必需字段。
     */
    public boolean isComplete() {
        return hasText(tenantId)
                && hasText(sourceSystem)
                && hasText(externalUserId)
                && hasText(displayName);
    }

    /**
     * 用默认上下文补齐显式上下文里缺失的字段，但不会覆盖已显式传入的值。
     */
    public AccessContext fillMissing(AccessContext defaults) {
        Map<String, Boolean> mergedPermissions = new LinkedHashMap<>(defaults.permissions());
        mergedPermissions.putAll(permissions);
        return new AccessContext(
                firstNonBlank(tenantId, defaults.tenantId()),
                firstNonBlank(sourceSystem, defaults.sourceSystem()),
                firstNonBlank(externalUserId, defaults.externalUserId()),
                firstNonBlank(displayName, defaults.displayName()),
                mergedPermissions,
                source
        );
    }

    /**
     * 兼容仍然使用旧 `RequestContext` 的服务层。
     */
    public RequestContext toRequestContext() {
        return new RequestContext(tenantId, sourceSystem, externalUserId, displayName);
    }

    public String actorUser() {
        return externalUserId;
    }

    public String actorName() {
        return displayName;
    }

    public boolean permission(String key, boolean defaultValue) {
        return permissions.getOrDefault(key, defaultValue);
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : fallback;
    }
}
