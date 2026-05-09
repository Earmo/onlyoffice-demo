package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.storage.enums.StorageProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageProviderResolverTest {

    @Test
    void shouldPreferTenantRoutingOverSourceSystemRouting() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.LOCAL,
                Map.of("tenant-a", StorageProvider.MINIO),
                Map.of("native", StorageProvider.LOCAL)
        ));

        StorageProvider provider = resolver.resolve(new RequestContext("tenant-a", "native", "user-a", "Alice"));

        assertThat(provider).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    void shouldFallbackToSourceSystemRoutingWhenTenantIsNotConfigured() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.LOCAL,
                Map.of(),
                Map.of("oa", StorageProvider.MINIO)
        ));

        StorageProvider provider = resolver.resolve(new RequestContext("tenant-a", "oa", "user-a", "Alice"));

        assertThat(provider).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    void shouldFallbackToDefaultProviderWhenNoRoutingMatches() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.LOCAL,
                Map.of("tenant-b", StorageProvider.MINIO),
                Map.of("erp", StorageProvider.MINIO)
        ));

        StorageProvider provider = resolver.resolve(new RequestContext("tenant-a", "native", "user-a", "Alice"));

        assertThat(provider).isEqualTo(StorageProvider.LOCAL);
    }

    @Test
    void shouldResolveProviderFromDocumentEntity() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.LOCAL,
                Map.of("tenant-a", StorageProvider.MINIO),
                Map.of()
        ));
        DocumentMetadataEntity entity = new DocumentMetadataEntity();
        entity.setTenantId("tenant-a");
        entity.setSourceSystem("native");

        assertThat(resolver.resolve(entity)).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    void shouldUseDefaultProviderWhenRequestContextIsMissing() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.MINIO,
                Map.of("tenant-a", StorageProvider.LOCAL),
                Map.of("native", StorageProvider.LOCAL)
        ));

        assertThat(resolver.resolve((RequestContext) null)).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    void shouldAllowTenantRoutingToCos() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.MINIO,
                Map.of("tenant-cos", StorageProvider.COS),
                Map.of("native", StorageProvider.LOCAL)
        ));

        StorageProvider provider = resolver.resolve(new RequestContext("tenant-cos", "native", "user-a", "Alice"));

        assertThat(provider).isEqualTo(StorageProvider.COS);
    }

    @Test
    void shouldAllowSourceSystemRoutingToCosWhenTenantIsMissing() {
        StorageProviderResolver resolver = new StorageProviderResolver(properties(
                StorageProvider.LOCAL,
                Map.of(),
                Map.of("tencent-docs", StorageProvider.COS)
        ));

        StorageProvider provider = resolver.resolve(new RequestContext("tenant-a", "tencent-docs", "user-a", "Alice"));

        assertThat(provider).isEqualTo(StorageProvider.COS);
    }

    private OnlyofficeIntegrationProperties properties(
            StorageProvider defaultProvider,
            Map<String, StorageProvider> tenantMappings,
            Map<String, StorageProvider> sourceSystemMappings
    ) {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();

        OnlyofficeIntegrationProperties.StorageProperties storageProperties =
                new OnlyofficeIntegrationProperties.StorageProperties();
        storageProperties.setDefaultProvider(defaultProvider);

        OnlyofficeIntegrationProperties.RoutingProperties routingProperties =
                new OnlyofficeIntegrationProperties.RoutingProperties();
        routingProperties.setTenants(tenantMappings);
        routingProperties.setSourceSystems(sourceSystemMappings);
        storageProperties.setRouting(routingProperties);

        properties.setStorage(storageProperties);
        return properties;
    }
}
