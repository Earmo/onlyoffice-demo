package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.NormalizedDocumentMetadata;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.RemoteResourceSecurityService;
import com.earmo.onlyoffice.integration.storage.*;
import com.mybatisflex.core.keygen.impl.ULIDKeyGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 文档文件对象编排服务默认实现。
 *
 * <p>这个服务不是文档主数据真相源，而是“文件对象编排门面”：
 * 1. 先根据请求上下文或文档实体选择 storage provider；
 * 2. 再生成稳定的 storageKey，把对象身份和厂商实现解耦；
 * 3. 最后组织建档、上传、导入、callback 回写与失败补偿流程。
 *
 * <p>也就是说，数据库负责“文档是谁、当前摘要状态是什么”，
 * 这里负责“对象内容放到哪里、怎么写、失败时如何尽量补偿”。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageServiceImpl implements DocumentStorageService {

    private static final String DEFAULT_EXTENSION = "docx";
    private static final Duration CALLBACK_DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CALLBACK_DOWNLOAD_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> OOXML_EXTENSIONS = Set.of("docx", "xlsx", "pptx");
    private static final Set<String> ODF_EXTENSIONS = Set.of("odt", "ods", "odp");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "csv");
    private static final ULIDKeyGenerator DOCUMENT_ID_GENERATOR = new ULIDKeyGenerator();
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "doc", "docx", "odt", "rtf", "txt",
            "xls", "xlsx", "ods", "csv",
            "ppt", "pptx", "odp",
            "pdf"
    );
    private static final Map<String, Set<MediaType>> SUPPORTED_DOCUMENT_MEDIA_TYPES = Map.ofEntries(
            Map.entry("doc", Set.of(MediaType.parseMediaType("application/msword"))),
            Map.entry("docx", Set.of(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))),
            Map.entry("odt", Set.of(MediaType.parseMediaType("application/vnd.oasis.opendocument.text"))),
            Map.entry("rtf", Set.of(MediaType.parseMediaType("application/rtf"), MediaType.parseMediaType("text/rtf"))),
            Map.entry("txt", Set.of(MediaType.TEXT_PLAIN)),
            Map.entry("csv", Set.of(MediaType.parseMediaType("text/csv"), MediaType.parseMediaType("application/csv"), MediaType.parseMediaType("application/vnd.ms-excel"))),
            Map.entry("xls", Set.of(MediaType.parseMediaType("application/vnd.ms-excel"))),
            Map.entry("xlsx", Set.of(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))),
            Map.entry("ods", Set.of(MediaType.parseMediaType("application/vnd.oasis.opendocument.spreadsheet"))),
            Map.entry("ppt", Set.of(MediaType.parseMediaType("application/vnd.ms-powerpoint"))),
            Map.entry("pptx", Set.of(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation"))),
            Map.entry("odp", Set.of(MediaType.parseMediaType("application/vnd.oasis.opendocument.presentation"))),
            Map.entry("pdf", Set.of(MediaType.APPLICATION_PDF))
    );

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
    private final DocumentMetadataService documentMetadataService;
    private final RestClient.Builder restClientBuilder;
    private final List<DocumentStorageStrategy> documentStorageStrategies;
    private final StorageProviderResolver storageProviderResolver;
    private final StorageKeyFactory storageKeyFactory;
    private final RemoteResourceSecurityService remoteResourceSecurityService;

    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final RestClient restClient = buildRestClient();

    /**
     * 确保引导文档存在，不存在时创建默认 docx。
     *
     * @param rawDocumentId 原始文档标识
     * @return 已存在或新建的存储文档
     * @throws IOException 文档对象读写失败时抛出
     */
    @Override
    public StoredDocument ensureBootstrapDocument(String rawDocumentId) throws IOException {
        String documentId = sanitizeDocumentId(rawDocumentId);
        if (documentMetadataService.findDocument(documentId).isPresent()) {
            return getRequiredDocument(documentId);
        }

        RequestContext requestContext = defaultRequestContext();
        String title = documentId + "." + DEFAULT_EXTENSION;
        String storageKey = storageKeyFactory.build(requestContext, documentId, DEFAULT_EXTENSION);
        DocumentStorageStrategy strategy = resolveStrategy(requestContext);

        if (!strategy.exists(storageKey)) {
            strategy.writeNew(new StorageWriteRequest(storageKey, contentTypeFor(title), createBootstrapDocx()));
        }

        try {
            DocumentMetadataEntity entity = documentMetadataService.createDocument(
                    documentId,
                    title,
                    DEFAULT_EXTENSION,
                    resolveDocumentType(DEFAULT_EXTENSION),
                    storageKey,
                    requestContext,
                    null
            );
            return getRequiredDocument(entity.getDocumentId());
        } catch (RuntimeException ex) {
            deleteQuietly(strategy, storageKey);
            throw ex;
        }
    }

    /**
     * 获取必须存在且可访问的文档。
     *
     * @param rawDocumentId 原始文档标识
     * @return 存储文档信息
     * @throws IOException 文档内容不存在或读取失败时抛出
     */
    @Override
    public StoredDocument getRequiredDocument(String rawDocumentId) throws IOException {
        String documentId = sanitizeDocumentId(rawDocumentId);
        DocumentMetadataEntity entity = documentMetadataService.requireAccessibleDocument(documentId);
        DocumentStorageStrategy strategy = resolveStrategy(entity);
        if (!strategy.exists(entity.getStorageKey())) {
            throw new IOException("文档内容不存在：" + entity.getStorageKey());
        }
        StoredObjectResource objectResource = strategy.read(entity.getStorageKey());
        DocumentMetadataEntity normalizedEntity = normalizeDocumentMetadataIfNeeded(entity, objectResource.body(), null);
        return toStoredDocument(normalizedEntity, objectResource);
    }

    /**
     * 读取文档二进制内容。
     *
     * @param rawDocumentId 原始文档标识
     * @return 文档字节数组
     * @throws IOException 文档读取失败时抛出
     */
    @Override
    public byte[] readDocument(String rawDocumentId) throws IOException {
        String documentId = sanitizeDocumentId(rawDocumentId);
        DocumentMetadataEntity entity = documentMetadataService.requireAccessibleDocument(documentId);
        DocumentStorageStrategy strategy = resolveStrategy(entity);
        StoredObjectResource objectResource = strategy.read(entity.getStorageKey());
        normalizeDocumentMetadataIfNeeded(entity, objectResource.body(), null);
        return objectResource.body();
    }

    /**
     * callback 回写只负责“把最新文件对象覆盖到共享存储”。
     *
     * <p>主表摘要状态、运行事件流和审计事件都在其他服务里更新；
     * 这里保持单一职责，只处理下载最新文件并覆盖对象内容。
     *
     * @param rawDocumentId    原始文档标识
     * @param downloadUrl      ONLYOFFICE 回调提供的最新文件下载地址
     * @param callbackFileType ONLYOFFICE 回调提示的文件类型
     * @return 规范化后的文档元数据
     * @throws IOException 下载或回写文档失败时抛出
     */
    @Override
    public NormalizedDocumentMetadata saveCallbackDocument(String rawDocumentId, String downloadUrl, String callbackFileType)
            throws IOException {
        if (!StringUtils.hasText(downloadUrl)) {
            throw new IOException("ONLYOFFICE callback 缺少最新文档下载地址。");
        }

        String documentId = sanitizeDocumentId(rawDocumentId);
        DocumentMetadataEntity entity = documentMetadataService.requireAccessibleDocument(documentId);
        DocumentStorageStrategy strategy = resolveStrategy(entity);
        String resolvedDownloadUrl = resolveAccessibleCallbackDownloadUrl(downloadUrl);
        byte[] latestFile = getRestClient().get()
                .uri(resolvedDownloadUrl)
                .retrieve()
                .body(byte[].class);

        if (latestFile == null || latestFile.length == 0) {
            throw new IOException("ONLYOFFICE callback did not return file bytes.");
        }

        NormalizedDocumentMetadata normalizedMetadata = normalizeDocumentMetadata(entity, latestFile, callbackFileType);
        strategy.overwrite(
                new StorageWriteRequest(entity.getStorageKey(), contentTypeFor(normalizedMetadata.title()), latestFile)
        );
        normalizeDocumentMetadataIfNeeded(entity, latestFile, callbackFileType);
        return normalizedMetadata;
    }

    /**
     * 使用默认请求上下文保存上传文档。
     *
     * @param originalFilename 原始文件名
     * @param body             文件内容
     * @return 存储文档信息
     * @throws IOException 文件写入失败时抛出
     */
    @Override
    public StoredDocument storeUploadedDocument(String originalFilename, byte[] body) throws IOException {
        return storeUploadedDocument(originalFilename, body, defaultRequestContext());
    }

    /**
     * 上传链路采用“先写对象，再落元数据”的顺序。
     *
     * <p>这样可以避免数据库先成功、对象写入后失败时留下半成品文档。
     * 一旦对象写入成功但元数据插入失败，会尝试 best-effort 删除对象，减少脏数据残留。
     *
     * @param originalFilename 原始文件名
     * @param body             文件内容
     * @param requestContext   请求上下文
     * @return 存储文档信息
     * @throws IOException 文件写入或读取失败时抛出
     */
    @Override
    public StoredDocument storeUploadedDocument(String originalFilename, byte[] body, RequestContext requestContext)
            throws IOException {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("上传文件不能为空。");
        }

        String normalizedFilename = normalizeFilename(originalFilename);
        String extension = requireSupportedExtension(normalizedFilename);
        String documentId = generateDocumentId();
        String storageKey = storageKeyFactory.build(requestContext, documentId, extension);
        DocumentStorageStrategy strategy = resolveStrategy(requestContext);
        strategy.writeNew(new StorageWriteRequest(storageKey, contentTypeFor(normalizedFilename), body));

        try {
            DocumentMetadataEntity entity = documentMetadataService.createDocument(
                    documentId,
                    normalizedFilename,
                    extension,
                    resolveDocumentType(extension),
                    storageKey,
                    requestContext,
                    null
            );
            return getRequiredDocument(entity.getDocumentId());
        } catch (RuntimeException ex) {
            deleteQuietly(strategy, storageKey);
            throw ex;
        }
    }

    /**
     * 使用默认请求上下文导入远程文档。
     *
     * @param sourceUrl 远程文档地址
     * @return 存储文档信息
     * @throws IOException 远程下载或文件写入失败时抛出
     */
    @Override
    public StoredDocument importRemoteDocument(String sourceUrl) throws IOException {
        return importRemoteDocument(sourceUrl, defaultRequestContext());
    }

    /**
     * 远程导入复用上传链路，但会多出一步远程资源安全校验。
     *
     * <p>步骤顺序是：
     * 1. 先校验 URL 协议和主机，阻断 SSRF；
     * 2. 下载时限制大小；
     * 3. 校验扩展名与响应媒体类型；
     * 4. 最终再交给上传链路完成对象写入和元数据创建。
     *
     * @param sourceUrl      远程文档地址
     * @param requestContext 请求上下文
     * @return 存储文档信息
     * @throws IOException 远程下载或文件写入失败时抛出
     */
    @Override
    public StoredDocument importRemoteDocument(String sourceUrl, RequestContext requestContext) throws IOException {
        URI remoteUri = parseAndValidateRemoteUrl(sourceUrl);
        log.info("开始导入远程文档：sourceUrl={}, tenantId={}, actorUser={}", sourceUrl, requestContext.tenantId(), requestContext.externalUser());
        RemoteResourceSecurityService.RemoteFetchResult remoteFetchResult = remoteResourceSecurityService.fetch(
                remoteUri,
                onlyofficeIntegrationProperties.getRemoteResource().getMaxDocumentBytes(),
                "远程文档"
        );
        String originalFilename = resolveRemoteFilename(remoteUri, remoteFetchResult.suggestedFilename());
        validateRemoteDocumentMediaType(originalFilename, remoteFetchResult.mediaType());
        log.info(
                "远程文档下载完成：sourceUrl={}, resolvedFilename={}, contentType={}, size={} bytes",
                sourceUrl,
                originalFilename,
                remoteFetchResult.mediaType(),
                remoteFetchResult.body().length
        );

        StoredDocument storedDocument = storeUploadedDocument(originalFilename, remoteFetchResult.body(), requestContext);
        log.info(
                "远程文档导入完成：sourceUrl={}, documentId={}, title={}, storageKey={}",
                sourceUrl,
                storedDocument.documentId(),
                storedDocument.title(),
                storedDocument.storageKey()
        );
        return storedDocument;
    }

    /**
     * 显式创建原生文档时，仍然沿用“对象先写入、主数据后创建”的顺序。
     *
     * <p>这样无论底层用的是 local、minio 还是后续的 cos，行为都保持一致。
     *
     * @param rawDocumentId      调用方传入的原始文档标识，当前仅用于兼容接口语义
     * @param rawTitle           原始文档标题
     * @param requestContext     请求上下文
     * @param externalDocumentId 外部系统文档标识
     * @return 存储文档信息
     * @throws IOException 文件写入或读取失败时抛出
     */
    @Override
    public StoredDocument createNativeDocument(
            String rawDocumentId,
            String rawTitle,
            RequestContext requestContext,
            String externalDocumentId
    ) throws IOException {
        String title = StringUtils.hasText(rawTitle) ? rawTitle.trim() : "untitled.docx";
        String extension = requireSupportedExtension(title);
        if (!DEFAULT_EXTENSION.equals(extension)) {
            throw new IllegalArgumentException("当前显式创建接口只支持 docx 文档。");
        }

        String documentId = generateDocumentId();
        String storageKey = storageKeyFactory.build(requestContext, documentId, extension);
        DocumentStorageStrategy strategy = resolveStrategy(requestContext);
        if (!strategy.exists(storageKey)) {
            strategy.writeNew(new StorageWriteRequest(storageKey, contentTypeFor(title), createBootstrapDocx()));
        }

        try {
            DocumentMetadataEntity entity = documentMetadataService.createDocument(
                    documentId,
                    title,
                    extension,
                    resolveDocumentType(extension),
                    storageKey,
                    requestContext,
                    externalDocumentId
            );
            rollbackGeneratedObjectIfEntityReused(strategy, storageKey, entity);
            return getRequiredDocument(entity.getDocumentId());
        } catch (RuntimeException ex) {
            deleteQuietly(strategy, storageKey);
            throw ex;
        }
    }

    /**
     * 判断文档对象是否存在于对应存储中。
     *
     * @param entity 文档元数据实体
     * @return 文档对象存在时返回 true
     * @throws IOException 存储访问失败时抛出
     */
    @Override
    public boolean exists(DocumentMetadataEntity entity) throws IOException {
        return resolveStrategy(entity).exists(entity.getStorageKey());
    }

    /**
     * 解析文档对应的存储 provider。
     *
     * @param entity 文档元数据实体
     * @return 存储 provider
     */
    @Override
    public StorageProvider resolveProvider(DocumentMetadataEntity entity) {
        return storageProviderResolver.resolve(entity);
    }

    /**
     * 通过懒加载方式初始化 RestClient，避免每次请求都重复 build。
     *
     * @return 用于下载 ONLYOFFICE 回调文件的 RestClient
     */
    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CALLBACK_DOWNLOAD_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(CALLBACK_DOWNLOAD_READ_TIMEOUT);
        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * ONLYOFFICE callback 返回的下载地址有时会带浏览器侧公开域名，甚至回退成 localhost。
     *
     * <p>这些地址对 server 容器未必可达，因此这里统一改写到 command service 的内网地址，
     * 避免 callback 下载最新文件时走宿主页反代、宿主机回环或其他不稳定链路。
     *
     * @param rawDownloadUrl ONLYOFFICE 回调提供的原始下载地址
     * @return 服务端可访问的下载地址
     * @throws IOException 下载地址非法时抛出
     */
    private String resolveAccessibleCallbackDownloadUrl(String rawDownloadUrl) throws IOException {
        String normalizedDownloadUrl = rawDownloadUrl.trim();
        URI originalUri = parseAbsoluteUri(normalizedDownloadUrl, "ONLYOFFICE callback 最新文件下载地址非法。");
        URI commandBaseUri = parseOptionalAbsoluteUri(onlyofficeIntegrationProperties.getDocumentServerCommandUrl());
        if (commandBaseUri == null) {
            return normalizedDownloadUrl;
        }

        String originalPath = normalizePath(originalUri.getRawPath());
        String rewrittenPath = stripKnownOnlyofficePublicPrefix(originalPath);
        boolean shouldRewrite = isLoopbackHost(originalUri.getHost())
                || sameOriginIgnorePath(originalUri, parseOptionalAbsoluteUri(onlyofficeIntegrationProperties.getDocumentServerUrl()))
                || sameOriginIgnorePath(originalUri, parseOptionalAbsoluteUri(onlyofficeIntegrationProperties.getPublicBaseUrl()))
                || !rewrittenPath.equals(originalPath);

        if (!shouldRewrite) {
            return normalizedDownloadUrl;
        }

        String resolvedUrl = UriComponentsBuilder.fromUri(commandBaseUri)
                .replacePath(joinPaths(commandBaseUri.getPath(), rewrittenPath))
                .replaceQuery(originalUri.getRawQuery())
                .build(true)
                .toUriString();

        log.info(
                "将 ONLYOFFICE callback 下载地址改写为容器可达地址：originalUrl={}, resolvedUrl={}",
                normalizedDownloadUrl,
                resolvedUrl
        );
        return resolvedUrl;
    }

    /**
     * 移除下载地址中已知的 ONLYOFFICE 公开访问前缀。
     *
     * @param rawPath 原始 URL path
     * @return 可拼接到内网 command 地址上的 path
     */
    private String stripKnownOnlyofficePublicPrefix(String rawPath) {
        String normalizedPath = normalizePath(rawPath);
        for (String prefix : List.of(
                normalizeOnlyofficePublicPath(onlyofficeIntegrationProperties.getDocumentServerUrl()),
                normalizeOnlyofficePublicPath(appendOnlyofficePath(onlyofficeIntegrationProperties.getPublicBaseUrl()))
        )) {
            if (!StringUtils.hasText(prefix) || "/".equals(prefix)) {
                continue;
            }
            if (normalizedPath.equals(prefix)) {
                return "/";
            }
            if (normalizedPath.startsWith(prefix + "/")) {
                return normalizedPath.substring(prefix.length());
            }
        }
        return normalizedPath;
    }

    /**
     * 在公开基础地址后追加 ONLYOFFICE 代理路径。
     *
     * @param publicBaseUrl 应用公开基础地址
     * @return 追加代理路径后的地址，配置缺失时返回空字符串
     */
    private String appendOnlyofficePath(String publicBaseUrl) {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return "";
        }
        return UriComponentsBuilder.fromHttpUrl(publicBaseUrl.trim()).path("/api/office").build().toUriString();
    }

    /**
     * 提取 ONLYOFFICE 公开地址中的规范化路径。
     *
     * @param rawUrl 原始公开地址
     * @return 规范化 path，地址非法时返回空字符串
     */
    private String normalizeOnlyofficePublicPath(String rawUrl) {
        URI uri = parseOptionalAbsoluteUri(rawUrl);
        return uri == null ? "" : normalizePath(uri.getPath());
    }

    /**
     * 尝试解析绝对 URI，配置为空或非法时返回 null。
     *
     * @param rawUrl 原始地址
     * @return 绝对 URI，无法解析时返回 null
     */
    private URI parseOptionalAbsoluteUri(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            return StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost()) ? uri : null;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    /**
     * 解析并校验绝对 URI。
     *
     * @param rawUrl       原始地址
     * @param errorMessage 校验失败时使用的错误文案
     * @return 绝对 URI
     * @throws IOException 地址非法时抛出
     */
    private URI parseAbsoluteUri(String rawUrl, String errorMessage) throws IOException {
        try {
            URI uri = new URI(rawUrl);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IOException(errorMessage);
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new IOException(errorMessage, ex);
        }
    }

    /**
     * 判断两个 URI 是否具有相同协议、主机和端口。
     *
     * @param left  左侧 URI
     * @param right 右侧 URI
     * @return 同源时返回 true
     */
    private boolean sameOriginIgnorePath(URI left, URI right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    /**
     * 解析 URI 的有效端口。
     *
     * @param uri URI
     * @return 显式端口或协议默认端口
     */
    private int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * 判断主机名是否指向本机回环。
     *
     * @param host 主机名
     * @return 回环地址时返回 true
     */
    private boolean isLoopbackHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host);
    }

    /**
     * 规范化 URL path。
     *
     * @param path 原始 path
     * @return 以斜杠开头的 path
     */
    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 拼接两个 URL path。
     *
     * @param basePath     基础 path
     * @param relativePath 相对 path
     * @return 拼接后的 path
     */
    private String joinPaths(String basePath, String relativePath) {
        String normalizedBase = normalizePath(basePath);
        String normalizedRelative = normalizePath(relativePath);
        if ("/".equals(normalizedBase)) {
            return normalizedRelative;
        }
        if ("/".equals(normalizedRelative)) {
            return normalizedBase;
        }
        return normalizedBase + normalizedRelative;
    }

    /**
     * 将元数据和对象资源投影为存储文档模型。
     *
     * @param entity         文档元数据实体
     * @param objectResource 存储对象资源
     * @return 存储文档模型
     */
    private StoredDocument toStoredDocument(DocumentMetadataEntity entity, StoredObjectResource objectResource) {
        return documentMetadataService.toStoredDocument(
                entity,
                objectResource.localPath(),
                objectResource.lastModified()
        );
    }

    /**
     * 根据请求上下文解析存储策略。
     *
     * @param requestContext 请求上下文
     * @return 存储策略
     */
    private DocumentStorageStrategy resolveStrategy(RequestContext requestContext) {
        return resolveStrategy(storageProviderResolver.resolve(requestContext));
    }

    /**
     * 根据文档元数据解析存储策略。
     *
     * @param entity 文档元数据实体
     * @return 存储策略
     */
    private DocumentStorageStrategy resolveStrategy(DocumentMetadataEntity entity) {
        return resolveStrategy(storageProviderResolver.resolve(entity));
    }

    /**
     * 根据存储 provider 解析具体策略实现。
     *
     * @param provider 存储 provider
     * @return 存储策略
     */
    private DocumentStorageStrategy resolveStrategy(StorageProvider provider) {
        return documentStorageStrategies.stream()
                .filter(strategy -> strategy.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到存储 provider 实现：" + provider));
    }

    /**
     * 这里只做 best-effort 补偿，不覆盖原始主异常。
     *
     * <p>如果建档主流程已经失败，补偿删除再失败也不应该吞掉真正导致业务失败的原因。
     *
     * @param strategy   存储策略
     * @param storageKey 存储对象键
     */
    private void deleteQuietly(DocumentStorageStrategy strategy, String storageKey) {
        try {
            strategy.delete(storageKey);
        } catch (IOException ignored) {
            // 补偿删除失败只记录为 best-effort，不覆盖主异常。
        }
    }

    /**
     * 获取文件扩展名。
     *
     * @param filename 文件名
     * @return 小写扩展名，无法识别时返回默认扩展名
     */
    private String getFileExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return DEFAULT_EXTENSION;
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 根据文件内容修正文档元数据，必要时回写主表。
     *
     * @param entity         文档元数据实体
     * @param body           文件内容
     * @param hintedFileType 外部提示的文件类型
     * @return 原始或更新后的文档元数据实体
     */
    private DocumentMetadataEntity normalizeDocumentMetadataIfNeeded(
            DocumentMetadataEntity entity,
            byte[] body,
            String hintedFileType
    ) {
        NormalizedDocumentMetadata normalizedMetadata = normalizeDocumentMetadata(entity, body, hintedFileType);
        if (!metadataChanged(entity, normalizedMetadata)) {
            return entity;
        }

        log.info(
                "根据实际文件内容自动修正文档元数据：documentId={}, title={} -> {}, fileType={} -> {}, documentType={} -> {}",
                entity.getDocumentId(),
                entity.getTitle(),
                normalizedMetadata.title(),
                entity.getFileType(),
                normalizedMetadata.fileType(),
                entity.getDocumentType(),
                normalizedMetadata.documentType()
        );
        return documentMetadataService.updateDocumentFormat(
                entity.getDocumentId(),
                normalizedMetadata.title(),
                normalizedMetadata.fileType(),
                normalizedMetadata.documentType()
        );
    }

    /**
     * 判断规范化结果是否改变了文档元数据。
     *
     * @param entity             文档元数据实体
     * @param normalizedMetadata 规范化后的文档元数据
     * @return 元数据发生变化时返回 true
     */
    private boolean metadataChanged(DocumentMetadataEntity entity, NormalizedDocumentMetadata normalizedMetadata) {
        return !normalizedMetadata.title().equals(entity.getTitle())
                || !normalizedMetadata.fileType().equals(entity.getFileType())
                || !normalizedMetadata.documentType().equals(entity.getDocumentType());
    }

    /**
     * 根据文件内容、提示类型和当前元数据生成规范化元数据。
     *
     * @param entity         文档元数据实体
     * @param body           文件内容
     * @param hintedFileType 外部提示的文件类型
     * @return 规范化后的文档元数据
     */
    private NormalizedDocumentMetadata normalizeDocumentMetadata(
            DocumentMetadataEntity entity,
            byte[] body,
            String hintedFileType
    ) {
        String normalizedFileType = detectNormalizedFileType(body, hintedFileType, entity);
        String normalizedDocumentType = resolveDocumentType(normalizedFileType);
        String normalizedTitle = normalizeTitle(entity.getTitle(), entity.getDocumentId(), normalizedFileType);
        return new NormalizedDocumentMetadata(normalizedTitle, normalizedFileType, normalizedDocumentType);
    }

    /**
     * 检测文件真实扩展类型。
     *
     * @param body           文件内容
     * @param hintedFileType 外部提示的文件类型
     * @param entity         当前文档元数据实体
     * @return 规范化文件类型
     */
    private String detectNormalizedFileType(byte[] body, String hintedFileType, DocumentMetadataEntity entity) {
        String normalizedHint = normalizeSupportedExtension(hintedFileType);
        String normalizedCurrent = normalizeSupportedExtension(entity.getFileType());

        if (hasZipSignature(body)) {
            return detectZipBasedFileType(body, normalizedHint, normalizedCurrent, entity.getDocumentType());
        }
        if (hasOleSignature(body)) {
            return detectLegacyCompoundFileType(normalizedHint, normalizedCurrent, entity.getDocumentType());
        }
        if (hasPdfSignature(body)) {
            return "pdf";
        }
        if (isProbablyText(body)) {
            if (StringUtils.hasText(normalizedHint) && TEXT_EXTENSIONS.contains(normalizedHint)) {
                return normalizedHint;
            }
            if (StringUtils.hasText(normalizedCurrent) && TEXT_EXTENSIONS.contains(normalizedCurrent)) {
                return normalizedCurrent;
            }
        }
        if (StringUtils.hasText(normalizedHint)) {
            return normalizedHint;
        }
        if (StringUtils.hasText(normalizedCurrent)) {
            return normalizedCurrent;
        }
        return DEFAULT_EXTENSION;
    }

    /**
     * 识别 ZIP 容器类文档格式。
     *
     * @param body                文件内容
     * @param hintedFileType      外部提示的文件类型
     * @param currentFileType     当前文件类型
     * @param currentDocumentType 当前 ONLYOFFICE 文档类型
     * @return ZIP 容器内推断出的文件类型
     */
    private String detectZipBasedFileType(
            byte[] body,
            String hintedFileType,
            String currentFileType,
            String currentDocumentType
    ) {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/")) {
                    return "docx";
                }
                if (name.startsWith("xl/")) {
                    return "xlsx";
                }
                if (name.startsWith("ppt/")) {
                    return "pptx";
                }
                if ("mimetype".equals(name)) {
                    String mimetype = new String(zipInputStream.readNBytes(256), StandardCharsets.UTF_8);
                    if (mimetype.contains("application/vnd.oasis.opendocument.text")) {
                        return "odt";
                    }
                    if (mimetype.contains("application/vnd.oasis.opendocument.spreadsheet")) {
                        return "ods";
                    }
                    if (mimetype.contains("application/vnd.oasis.opendocument.presentation")) {
                        return "odp";
                    }
                }
            }
        } catch (IOException ex) {
            log.debug("解析 ZIP 文档格式失败，将回退到上下文推断：{}", ex.getMessage());
        }

        if (StringUtils.hasText(hintedFileType)
                && (OOXML_EXTENSIONS.contains(hintedFileType) || ODF_EXTENSIONS.contains(hintedFileType))) {
            return hintedFileType;
        }
        if (StringUtils.hasText(currentFileType)
                && (OOXML_EXTENSIONS.contains(currentFileType) || ODF_EXTENSIONS.contains(currentFileType))) {
            return currentFileType;
        }
        return switch (currentDocumentType) {
            case "cell" -> "xlsx";
            case "slide" -> "pptx";
            default -> "docx";
        };
    }

    /**
     * 识别传统 OLE 复合文档格式。
     *
     * @param hintedFileType      外部提示的文件类型
     * @param currentFileType     当前文件类型
     * @param currentDocumentType 当前 ONLYOFFICE 文档类型
     * @return 推断出的传统 Office 文件类型
     */
    private String detectLegacyCompoundFileType(String hintedFileType, String currentFileType, String currentDocumentType) {
        if (StringUtils.hasText(hintedFileType) && Set.of("doc", "xls", "ppt").contains(hintedFileType)) {
            return hintedFileType;
        }
        if (StringUtils.hasText(currentFileType) && Set.of("doc", "xls", "ppt").contains(currentFileType)) {
            return currentFileType;
        }
        return switch (currentDocumentType) {
            case "cell" -> "xls";
            case "slide" -> "ppt";
            default -> "doc";
        };
    }

    /**
     * 规范化并校验文件扩展类型。
     *
     * @param rawFileType 原始文件类型
     * @return 支持的规范化文件类型，不支持时返回 null
     */
    private String normalizeSupportedExtension(String rawFileType) {
        if (!StringUtils.hasText(rawFileType)) {
            return null;
        }
        String normalized = rawFileType.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(normalized) ? normalized : null;
    }

    /**
     * 判断文件内容是否具有 ZIP 签名。
     *
     * @param body 文件内容
     * @return 具有 ZIP 签名时返回 true
     */
    private boolean hasZipSignature(byte[] body) {
        return body != null
                && body.length >= 4
                && body[0] == 'P'
                && body[1] == 'K'
                && (body[2] == 3 || body[2] == 5 || body[2] == 7)
                && (body[3] == 4 || body[3] == 6 || body[3] == 8);
    }

    /**
     * 判断文件内容是否具有 OLE 复合文档签名。
     *
     * @param body 文件内容
     * @return 具有 OLE 签名时返回 true
     */
    private boolean hasOleSignature(byte[] body) {
        return body != null
                && body.length >= 8
                && (body[0] & 0xFF) == 0xD0
                && (body[1] & 0xFF) == 0xCF
                && (body[2] & 0xFF) == 0x11
                && (body[3] & 0xFF) == 0xE0
                && (body[4] & 0xFF) == 0xA1
                && (body[5] & 0xFF) == 0xB1
                && (body[6] & 0xFF) == 0x1A
                && (body[7] & 0xFF) == 0xE1;
    }

    /**
     * 判断文件内容是否具有 PDF 签名。
     *
     * @param body 文件内容
     * @return 具有 PDF 签名时返回 true
     */
    private boolean hasPdfSignature(byte[] body) {
        return body != null
                && body.length >= 4
                && body[0] == '%'
                && body[1] == 'P'
                && body[2] == 'D'
                && body[3] == 'F';
    }

    /**
     * 粗略判断文件内容是否为文本。
     *
     * @param body 文件内容
     * @return 看起来像文本内容时返回 true
     */
    private boolean isProbablyText(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }

        int sampleLength = Math.min(body.length, 1024);
        int suspiciousControlBytes = 0;
        for (int index = 0; index < sampleLength; index++) {
            int unsigned = body[index] & 0xFF;
            if (unsigned == 0) {
                return false;
            }
            if (unsigned < 0x09 || (unsigned > 0x0D && unsigned < 0x20)) {
                suspiciousControlBytes++;
            }
        }
        return suspiciousControlBytes <= Math.max(1, sampleLength / 20);
    }

    /**
     * 根据文件类型解析 ONLYOFFICE 文档类型。
     *
     * @param fileType 文件扩展类型
     * @return ONLYOFFICE 文档类型
     */
    private String resolveDocumentType(String fileType) {
        return switch (fileType) {
            case "csv", "xls", "xlsx", "ods" -> "cell";
            case "ppt", "pptx", "odp" -> "slide";
            case "pdf" -> "pdf";
            default -> "word";
        };
    }

    /**
     * 清洗文档 ID。
     *
     * @param rawDocumentId 原始文档标识
     * @return 可安全用于业务和存储键的文档标识
     */
    private String sanitizeDocumentId(String rawDocumentId) {
        if (!StringUtils.hasText(rawDocumentId)) {
            return "sample";
        }

        String sanitized = rawDocumentId.trim().replaceAll("[^a-zA-Z0-9_-]", "-");
        return StringUtils.hasText(sanitized) ? sanitized : "sample";
    }

    /**
     * 要求文件名包含受支持扩展名。
     *
     * @param filename 文件名
     * @return 支持的文件扩展名
     */
    private String requireSupportedExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空。");
        }

        String extension = getFileExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("暂不支持该文档类型：" + extension);
        }
        return extension;
    }

    /**
     * 生成新的文档 ID。
     *
     * @return 单调 ULID 文档标识
     */
    private String generateDocumentId() {
        return DOCUMENT_ID_GENERATOR.nextMonotonicId();
    }

    /**
     * 当建档复用了已有实体时删除本次临时生成的对象。
     *
     * @param strategy            存储策略
     * @param generatedStorageKey 本次生成的对象键
     * @param entity              元数据服务返回的实体
     */
    private void rollbackGeneratedObjectIfEntityReused(
            DocumentStorageStrategy strategy,
            String generatedStorageKey,
            DocumentMetadataEntity entity
    ) {
        if (!generatedStorageKey.equals(entity.getStorageKey())) {
            deleteQuietly(strategy, generatedStorageKey);
        }
    }

    /**
     * 去除文件名扩展名。
     *
     * @param filename 文件名
     * @return 不含扩展名的基础文件名
     */
    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index <= 0) {
            return filename;
        }
        return filename.substring(0, index);
    }

    /**
     * 根据当前标题、文档 ID 和文件类型生成规范标题。
     *
     * @param currentTitle 当前标题
     * @param documentId   文档唯一标识
     * @param fileType     文件扩展类型
     * @return 规范化标题
     */
    private String normalizeTitle(String currentTitle, String documentId, String fileType) {
        String baseName = StringUtils.hasText(currentTitle) ? stripExtension(currentTitle.trim()) : documentId;
        if (!StringUtils.hasText(baseName)) {
            baseName = documentId;
        }
        return baseName + "." + fileType;
    }

    /**
     * 规范化上传或远程响应中的文件名。
     *
     * @param filename 原始文件名
     * @return 规范化后的文件名
     */
    private String normalizeFilename(String filename) {
        String normalized = StringUtils.getFilename(filename);
        String sanitized = StringUtils.hasText(normalized) ? normalized : filename;
        if (!StringUtils.hasText(sanitized) || !sanitized.contains("%")) {
            return sanitized;
        }

        try {
            return UriUtils.decode(sanitized, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return sanitized;
        }
    }

    /**
     * 解析并校验远程文档 URL。
     *
     * @param sourceUrl 远程文档地址
     * @return 通过安全校验的 URI
     */
    private URI parseAndValidateRemoteUrl(String sourceUrl) {
        return remoteResourceSecurityService.validateRemoteUri(sourceUrl, "网络文档地址");
    }

    /**
     * 从远程 URI 路径提取文件名。
     *
     * @param remoteUri 远程文档 URI
     * @return 远程文件名
     */
    private String extractRemoteFilename(URI remoteUri) {
        String path = remoteUri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("网络文档地址缺少文件名。");
        }

        String filename = Path.of(path).getFileName().toString();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("网络文档地址缺少文件名。");
        }

        requireSupportedExtension(filename);
        return filename;
    }

    /**
     * 优先使用响应建议文件名，否则从远程 URI 提取文件名。
     *
     * @param remoteUri         远程文档 URI
     * @param suggestedFilename 响应建议文件名
     * @return 可用于入库的远程文件名
     */
    private String resolveRemoteFilename(URI remoteUri, String suggestedFilename) {
        String normalizedSuggestedFilename = normalizeFilename(suggestedFilename);
        if (StringUtils.hasText(normalizedSuggestedFilename)) {
            requireSupportedExtension(normalizedSuggestedFilename);
            return normalizedSuggestedFilename;
        }
        return extractRemoteFilename(remoteUri);
    }

    /**
     * 校验远程文档响应媒体类型与文件扩展名是否匹配。
     *
     * @param filename  文件名
     * @param mediaType 响应媒体类型
     */
    private void validateRemoteDocumentMediaType(String filename, MediaType mediaType) {
        if (mediaType == null) {
            throw new IllegalArgumentException("远程文档响应缺少 Content-Type，无法确认文档类型。");
        }

        String extension = requireSupportedExtension(filename);
        Set<MediaType> allowedMediaTypes = SUPPORTED_DOCUMENT_MEDIA_TYPES.getOrDefault(extension, Set.of());
        boolean matched = allowedMediaTypes.stream().anyMatch(allowed -> allowed.isCompatibleWith(mediaType));
        if (!matched) {
            throw new IllegalArgumentException(
                    "远程文档类型校验失败：文件扩展名 ." + extension + " 与响应类型 " + mediaType + " 不匹配。"
            );
        }
    }

    /**
     * 构造默认请求上下文。
     *
     * @return 默认请求上下文
     */
    private RequestContext defaultRequestContext() {
        return new RequestContext(
                onlyofficeIntegrationProperties.getDefaultTenantId(),
                onlyofficeIntegrationProperties.getDefaultSourceSystem(),
                onlyofficeIntegrationProperties.getDefaultUser(),
                onlyofficeIntegrationProperties.getDefaultUserName()
        );
    }

    /**
     * 根据文件名推断 Content-Type。
     *
     * @param filename 文件名
     * @return Content-Type 字符串
     */
    private String contentTypeFor(String filename) {
        return MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
    }

    /**
     * 当系统还没有任何模板文件时，仍然需要生成一个合法可编辑的 docx。
     *
     * <p>这里手工构造最小 OpenXML 包，而不是依赖额外模板文件：
     * 1. `[Content_Types].xml` 声明文档类型；
     * 2. `_rels/.rels` 指向主文档；
     * 3. `word/document.xml` 放入一份可直接打开的初始内容。
     *
     * <p>这样无论部署环境是否预置模板，都能稳定创建 starter 的引导文档。
     *
     * @return 最小可编辑 docx 文件内容
     * @throws IOException 构造 ZIP 包失败时抛出
     */
    private byte[] createBootstrapDocx() throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            addZipEntry(zipOutputStream, "[Content_Types].xml", contentTypesXml());
            addZipEntry(zipOutputStream, "_rels/.rels", rootRelationshipsXml());
            addZipEntry(zipOutputStream, "word/document.xml", documentXml());
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    /**
     * 向 ZIP 输出流写入文本条目。
     *
     * @param zipOutputStream ZIP 输出流
     * @param name            条目名称
     * @param body            条目文本内容
     * @throws IOException 写入失败时抛出
     */
    private void addZipEntry(ZipOutputStream zipOutputStream, String name, String body) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    /**
     * 生成最小 docx 的 Content Types XML。
     *
     * @return Content Types XML
     */
    private String contentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """;
    }

    /**
     * 生成最小 docx 的根关系 XML。
     *
     * @return 根关系 XML
     */
    private String rootRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship
                      Id="rId1"
                      Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                      Target="word/document.xml"/>
                </Relationships>
                """;
    }

    /**
     * 生成最小 docx 的正文 XML。
     *
     * @return 正文 XML
     */
    private String documentXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
                            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
                            xmlns:o="urn:schemas-microsoft-com:office:office"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
                            xmlns:v="urn:schemas-microsoft-com:vml"
                            xmlns:wp14="http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing"
                            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                            xmlns:w10="urn:schemas-microsoft-com:office:word"
                            xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
                            xmlns:w15="http://schemas.microsoft.com/office/word/2012/wordml"
                            xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
                            xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk"
                            xmlns:wne="http://schemas.microsoft.com/office/word/2006/wordml"
                            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
                            mc:Ignorable="w14 w15 wp14">
                  <w:body>
                    <w:p>
                      <w:r>
                        <w:t>ONLYOFFICE Integration Starter</w:t>
                      </w:r>
                    </w:p>
                    <w:p>
                      <w:r>
                        <w:t>这是 starter 服务生成的引导文档。</w:t>
                      </w:r>
                    </w:p>
                    <w:p>
                      <w:r>
                        <w:t>你可以直接在编辑器里修改内容，然后关闭页面或点击保存。</w:t>
                      </w:r>
                    </w:p>
                    <w:p>
                      <w:r>
                        <w:t>ONLYOFFICE 会通过 callback 把结果回写到共享存储。</w:t>
                      </w:r>
                    </w:p>
                    <w:sectPr>
                      <w:pgSz w:w="11906" w:h="16838"/>
                      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
                      <w:cols w:space="708"/>
                      <w:docGrid w:linePitch="360"/>
                    </w:sectPr>
                  </w:body>
                </w:document>
                """;
    }
}
