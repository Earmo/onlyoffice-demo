package com.earmo.onlyoffice.integration.storage.impl.cos;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一创建和复用腾讯云 COS Java 客户端。
 *
 * <p>这里把凭证、地域和 endpoint 后缀收口到一处，避免策略实现里重复拼装 SDK 配置。
 */
@Component
@RequiredArgsConstructor
public class CosClientFactory {

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final COSClient client = buildClient();

    public COSClient client() {
        return getClient();
    }

    public String bucket() {
        return onlyofficeIntegrationProperties.getStorage().getCos().getBucket();
    }

    private COSClient buildClient() {
        OnlyofficeIntegrationProperties.CosStorageProperties properties =
                onlyofficeIntegrationProperties.getStorage().getCos();

        COSCredentials credentials = new BasicCOSCredentials(
                properties.getSecretId(),
                properties.getSecretKey()
        );
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        if (StringUtils.hasText(properties.getEndpointSuffix())) {
            clientConfig.setEndPointSuffix(properties.getEndpointSuffix().trim());
        }
        return new COSClient(credentials, clientConfig);
    }
}
