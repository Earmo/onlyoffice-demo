package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.storage.enums.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一解析一次文档操作应使用的 provider。
 *
 * <p>优先级固定为：tenant 路由 > sourceSystem 路由 > defaultProvider。
 * 后续如需接入 COS / OSS，只需要新增 provider 实现和映射配置，
 * controller 与 DocumentStorageService 的上层调用签名不需要变化。
 */
@Component
@RequiredArgsConstructor
public class StorageProviderResolver {

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

    /**
     * 根据请求上下文解析本次文档操作应使用的存储 provider。
     *
     * <p>请求上下文缺失时无法命中租户或来源系统路由，因此直接回退到默认 provider。
     *
     * @param requestContext 当前请求上下文，可为空
     * @return 最终选定的存储 provider
     */
    public StorageProvider resolve(RequestContext requestContext) {
        if (requestContext == null) {
            return onlyofficeIntegrationProperties.getStorage().getDefaultProvider();
        }
        return resolve(requestContext.tenantId(), requestContext.sourceSystem());
    }

    /**
     * 根据已持久化的文档元数据解析存储 provider。
     *
     * <p>用于读取、覆盖、删除已有文档时复用创建时记录的租户和来源系统，
     * 避免当前请求上下文变化导致路由到错误 provider。
     *
     * @param entity 文档元数据实体
     * @return 最终选定的存储 provider
     */
    public StorageProvider resolve(DocumentMetadataEntity entity) {
        return resolve(entity.getTenantId(), entity.getSourceSystem());
    }

    /**
     * 按固定优先级解析存储 provider。
     *
     * <p>优先级为：租户路由 > 来源系统路由 > 默认 provider。租户路由粒度最高，
     * 可覆盖来源系统的全局策略；来源系统路由用于同一业务系统整体迁移到指定存储。
     *
     * @param tenantId 租户 ID，可为空
     * @param sourceSystem 来源系统标识，可为空
     * @return 最终选定的存储 provider
     */
    public StorageProvider resolve(String tenantId, String sourceSystem) {
        StorageProvider tenantProvider = lookup(
                onlyofficeIntegrationProperties.getStorage().getRouting().getTenants(),
                tenantId
        );
        if (tenantProvider != null) {
            return tenantProvider;
        }

        StorageProvider sourceSystemProvider = lookup(
                onlyofficeIntegrationProperties.getStorage().getRouting().getSourceSystems(),
                sourceSystem
        );
        if (sourceSystemProvider != null) {
            return sourceSystemProvider;
        }

        return onlyofficeIntegrationProperties.getStorage().getDefaultProvider();
    }

    /**
     * 从路由映射中查找指定 key 对应的 provider。
     *
     * <p>这里只做首尾空白清理，不做大小写归一化，保证配置 key 的匹配规则明确可控。
     *
     * @param mappings 路由配置映射
     * @param key 待匹配的租户 ID 或来源系统标识
     * @return 命中时返回 provider；未配置、key 为空或未命中时返回 {@code null}
     */
    private StorageProvider lookup(java.util.Map<String, StorageProvider> mappings, String key) {
        if (!StringUtils.hasText(key) || mappings == null || mappings.isEmpty()) {
            return null;
        }
        return mappings.get(key.trim());
    }
}
