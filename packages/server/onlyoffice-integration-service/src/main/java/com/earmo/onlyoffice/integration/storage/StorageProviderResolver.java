package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
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

    public StorageProvider resolve(RequestContext requestContext) {
        if (requestContext == null) {
            return onlyofficeIntegrationProperties.getStorage().getDefaultProvider();
        }
        return resolve(requestContext.tenantId(), requestContext.sourceSystem());
    }

    public StorageProvider resolve(DocumentMetadataEntity entity) {
        return resolve(entity.getTenantId(), entity.getSourceSystem());
    }

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

    private StorageProvider lookup(java.util.Map<String, StorageProvider> mappings, String key) {
        if (!StringUtils.hasText(key) || mappings == null || mappings.isEmpty()) {
            return null;
        }
        return mappings.get(key.trim());
    }
}
