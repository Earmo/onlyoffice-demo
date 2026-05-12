package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.response.InsertImageResponse;
import com.earmo.onlyoffice.integration.model.RemoteImageResource;
import com.earmo.onlyoffice.integration.service.OnlyofficeImageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import com.earmo.onlyoffice.integration.service.RemoteResourceSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ONLYOFFICE 图片插入服务默认实现。
 *
 * <p>宿主页面不能持有 ONLYOFFICE 的 JWT 密钥，因此图片插入采用“后端生成参数 + 后端代理下载”的模式：
 * 1. 前端只把原始图片地址交给后端；
 * 2. 后端先校验 URL，再生成代理地址和签名；
 * 3. ONLYOFFICE Docs 实际拉图时访问的也是当前服务，而不是直接访问外部图片地址。
 */
@Service
@RequiredArgsConstructor
public class OnlyofficeImageServiceImpl implements OnlyofficeImageService {

    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of(
            "bmp", "gif", "jpg", "jpeg", "png", "svg", "tif", "tiff", "webp"
    );

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
    private final OnlyofficeJwtService onlyofficeJwtService;
    private final RemoteResourceSecurityService remoteResourceSecurityService;

    /**
     * 构造 ONLYOFFICE `insertImage` 所需的签名参数。
     *
     * <p>这里不把第三方图片 URL 直接暴露给浏览器或 ONLYOFFICE，
     * 而是统一换成当前服务的代理地址，这样远程资源安全策略和签名语义都由后端掌控。
     *
     * @param documentId 内部文档 ID。
     * @param sourceUrl  原始远程图片地址。
     * @return ONLYOFFICE 插入图片所需的命令参数。
     */
    @Override
    public InsertImageResponse buildInsertImageResponse(String documentId, String sourceUrl) {
        URI remoteUri = parseAndValidateSourceUrl(sourceUrl);
        String fileType = resolveFileType(remoteUri);
        String proxiedUrl = buildInternalImageProxyUrl(documentId, remoteUri.toString());

        Map<String, Object> insertImage = new LinkedHashMap<>();
        insertImage.put("c", "add");
        insertImage.put("fileType", fileType);
        insertImage.put("url", proxiedUrl);
        insertImage.put("token", onlyofficeJwtService.sign(insertImage));

        return new InsertImageResponse(insertImage);
    }

    /**
     * 代理下载远程图片，供 ONLYOFFICE Docs 拉取真实内容。
     *
     * <p>下载动作本身仍复用 RemoteResourceSecurityService 的 SSRF / 大小 / 媒体类型边界，
     * 这里只负责把下载结果重新包装成前端控制器可以直接返回的资源对象。
     *
     * @param sourceUrl 原始远程图片地址。
     * @return 可直接返回给 ONLYOFFICE Docs 的图片资源。
     * @throws IOException 远程图片下载失败时抛出。
     */
    @Override
    public RemoteImageResource proxyRemoteImage(String sourceUrl) throws IOException {
        URI remoteUri = parseAndValidateSourceUrl(sourceUrl);
        RemoteResourceSecurityService.RemoteFetchResult remoteFetchResult = remoteResourceSecurityService.fetch(
                remoteUri,
                onlyofficeIntegrationProperties.getRemoteResource().getMaxImageBytes(),
                "远程图片"
        );

        String filename = extractFilename(remoteUri);
        MediaType mediaType = remoteResourceSecurityService.requireImageMediaType(remoteFetchResult.mediaType());

        return new RemoteImageResource(remoteFetchResult.body(), mediaType, filename);
    }

    /**
     * 校验并解析远程图片地址。
     *
     * @param sourceUrl 原始远程图片地址。
     * @return 通过安全校验的 URI。
     */
    private URI parseAndValidateSourceUrl(String sourceUrl) {
        return remoteResourceSecurityService.validateRemoteUri(sourceUrl, "图片地址");
    }

    /**
     * 构造 ONLYOFFICE Docs 拉取图片时访问的内部代理地址。
     *
     * @param documentId 内部文档 ID。
     * @param sourceUrl  原始远程图片地址。
     * @return 当前服务的图片代理 URL。
     */
    private String buildInternalImageProxyUrl(String documentId, String sourceUrl) {
        return UriComponentsBuilder.fromHttpUrl(onlyofficeIntegrationProperties.getInternalBaseUrl())
                .path("/api/document-runtime/{documentId}/images/proxy")
                .queryParam("sourceUrl", sourceUrl)
                .buildAndExpand(documentId)
                .toUriString();
    }

    /**
     * 从远程 URI 文件名中解析 ONLYOFFICE 图片类型。
     *
     * @param remoteUri 远程图片 URI。
     * @return ONLYOFFICE add image 命令使用的 fileType。
     */
    private String resolveFileType(URI remoteUri) {
        String path = remoteUri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("图片地址缺少文件扩展名，无法识别图片类型。");
        }

        String filename = Paths.get(path).getFileName().toString();
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new IllegalArgumentException("图片地址缺少文件扩展名，当前示例无法识别图片类型。");
        }

        String extension = filename.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FILE_TYPES.contains(extension)) {
            throw new IllegalArgumentException("暂不支持该图片类型：" + extension);
        }

        return switch (extension) {
            case "jpeg" -> "jpg";
            case "tiff" -> "tif";
            default -> extension;
        };
    }

    /**
     * 从远程 URI 中提取图片文件名。
     *
     * @param remoteUri 远程图片 URI。
     * @return 文件名；无法提取时返回默认文件名。
     */
    private String extractFilename(URI remoteUri) {
        String path = remoteUri.getPath();
        if (!StringUtils.hasText(path)) {
            return "remote-image";
        }

        String filename = Paths.get(path).getFileName().toString();
        return StringUtils.hasText(filename) ? filename : "remote-image";
    }
}
