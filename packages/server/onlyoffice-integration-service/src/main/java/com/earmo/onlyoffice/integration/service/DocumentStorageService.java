package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.storage.DocumentStorageStrategy;
import com.earmo.onlyoffice.integration.storage.StorageKeyFactory;
import com.earmo.onlyoffice.integration.storage.StorageProvider;
import com.earmo.onlyoffice.integration.storage.StorageProviderResolver;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 负责文档文件内容的创建、读取和保存。
 *
 * <p>Phase 2 之后，这个服务不再自己直接读写本地文件系统，而是只负责编排：
 * 1. 根据文档上下文选择 storage provider；
 * 2. 生成稳定 storage key；
 * 3. 组织建档、上传、导入和 callback 回写的补偿语义；
 * 4. 把对象内容与数据库元数据重新聚合成 `StoredDocument`。
 */
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

  private static final String DEFAULT_EXTENSION = "docx";
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

  public StoredDocument getRequiredDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    DocumentMetadataEntity entity = documentMetadataService.requireDocument(documentId);
    DocumentStorageStrategy strategy = resolveStrategy(entity);
    if (!strategy.exists(entity.getStorageKey())) {
      throw new IOException("文档内容不存在：" + entity.getStorageKey());
    }
    return toStoredDocument(entity, strategy.read(entity.getStorageKey()));
  }

  public byte[] readDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    DocumentMetadataEntity entity = documentMetadataService.requireDocument(documentId);
    DocumentStorageStrategy strategy = resolveStrategy(entity);
    return strategy.read(entity.getStorageKey()).body();
  }

  public void saveCallbackDocument(String rawDocumentId, String downloadUrl) throws IOException {
    if (!StringUtils.hasText(downloadUrl)) {
      return;
    }

    String documentId = sanitizeDocumentId(rawDocumentId);
    DocumentMetadataEntity entity = documentMetadataService.requireDocument(documentId);
    DocumentStorageStrategy strategy = resolveStrategy(entity);
    byte[] latestFile = getRestClient().get()
        .uri(downloadUrl)
        .retrieve()
        .body(byte[].class);

    if (latestFile == null || latestFile.length == 0) {
      throw new IOException("ONLYOFFICE callback did not return file bytes.");
    }

    strategy.overwrite(new StorageWriteRequest(entity.getStorageKey(), contentTypeFor(entity.getTitle()), latestFile));
  }

  public StoredDocument storeUploadedDocument(String originalFilename, byte[] body) throws IOException {
    return storeUploadedDocument(originalFilename, body, defaultRequestContext());
  }

  public StoredDocument storeUploadedDocument(String originalFilename, byte[] body, RequestContext requestContext)
      throws IOException {
    if (body == null || body.length == 0) {
      throw new IllegalArgumentException("上传文件不能为空。");
    }

    String extension = requireSupportedExtension(originalFilename);
    String documentId = buildGeneratedDocumentId(stripExtension(originalFilename));
    String storageKey = storageKeyFactory.build(requestContext, documentId, extension);
    DocumentStorageStrategy strategy = resolveStrategy(requestContext);
    strategy.writeNew(new StorageWriteRequest(storageKey, contentTypeFor(originalFilename), body));

    try {
      DocumentMetadataEntity entity = documentMetadataService.createDocument(
          documentId,
          documentId + "." + extension,
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

  public StoredDocument importRemoteDocument(String sourceUrl) throws IOException {
    return importRemoteDocument(sourceUrl, defaultRequestContext());
  }

  public StoredDocument importRemoteDocument(String sourceUrl, RequestContext requestContext) throws IOException {
    URI remoteUri = parseAndValidateRemoteUrl(sourceUrl);
    String originalFilename = extractRemoteFilename(remoteUri);
    RemoteResourceSecurityService.RemoteFetchResult remoteFetchResult = remoteResourceSecurityService.fetch(
        remoteUri,
        onlyofficeIntegrationProperties.getRemoteResource().getMaxDocumentBytes(),
        "远程文档"
    );
    validateRemoteDocumentMediaType(originalFilename, remoteFetchResult.mediaType());

    return storeUploadedDocument(originalFilename, remoteFetchResult.body(), requestContext);
  }

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

    String documentId = StringUtils.hasText(rawDocumentId)
        ? sanitizeDocumentId(rawDocumentId)
        : buildGeneratedDocumentId(stripExtension(title));
    if (documentMetadataService.findDocument(documentId).isPresent()) {
      return getRequiredDocument(documentId);
    }

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
      return getRequiredDocument(entity.getDocumentId());
    } catch (RuntimeException ex) {
      deleteQuietly(strategy, storageKey);
      throw ex;
    }
  }

  public boolean exists(DocumentMetadataEntity entity) throws IOException {
    return resolveStrategy(entity).exists(entity.getStorageKey());
  }

  public StorageProvider resolveProvider(DocumentMetadataEntity entity) {
    return storageProviderResolver.resolve(entity);
  }

  /**
   * 通过懒加载方式初始化 RestClient，避免每次请求都重复 build，也避免为此保留样板构造器。
   */
  private RestClient buildRestClient() {
    return restClientBuilder.build();
  }

  private StoredDocument toStoredDocument(DocumentMetadataEntity entity, StoredObjectResource objectResource) {
    return documentMetadataService.toStoredDocument(
        entity,
        objectResource.localPath(),
        objectResource.lastModified()
    );
  }

  private DocumentStorageStrategy resolveStrategy(RequestContext requestContext) {
    return resolveStrategy(storageProviderResolver.resolve(requestContext));
  }

  private DocumentStorageStrategy resolveStrategy(DocumentMetadataEntity entity) {
    return resolveStrategy(storageProviderResolver.resolve(entity));
  }

  private DocumentStorageStrategy resolveStrategy(StorageProvider provider) {
    return documentStorageStrategies.stream()
        .filter(strategy -> strategy.provider() == provider)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("未找到存储 provider 实现：" + provider));
  }

  private void deleteQuietly(DocumentStorageStrategy strategy, String storageKey) {
    try {
      strategy.delete(storageKey);
    } catch (IOException ignored) {
      // 这里只做 best-effort 补偿，真正的建档失败原因仍以前面的主异常为准。
    }
  }

  private String getFileExtension(String filename) {
    int index = filename.lastIndexOf('.');
    if (index < 0 || index == filename.length() - 1) {
      return DEFAULT_EXTENSION;
    }
    return filename.substring(index + 1).toLowerCase(Locale.ROOT);
  }

  private String resolveDocumentType(String fileType) {
    return switch (fileType) {
      case "csv", "xls", "xlsx", "ods" -> "cell";
      case "ppt", "pptx", "odp" -> "slide";
      case "pdf" -> "pdf";
      default -> "word";
    };
  }

  private String sanitizeDocumentId(String rawDocumentId) {
    if (!StringUtils.hasText(rawDocumentId)) {
      return "sample";
    }

    String sanitized = rawDocumentId.trim().replaceAll("[^a-zA-Z0-9_-]", "-");
    return StringUtils.hasText(sanitized) ? sanitized : "sample";
  }

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

  private String buildGeneratedDocumentId(String filenameStem) {
    String baseName = sanitizeDocumentId(filenameStem);
    if ("sample".equals(baseName)) {
      baseName = "document";
    }
    return baseName + "-" + System.currentTimeMillis();
  }

  private String stripExtension(String filename) {
    int index = filename.lastIndexOf('.');
    if (index <= 0) {
      return filename;
    }
    return filename.substring(0, index);
  }

  private URI parseAndValidateRemoteUrl(String sourceUrl) {
    return remoteResourceSecurityService.validateRemoteUri(sourceUrl, "网络文档地址");
  }

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

  private RequestContext defaultRequestContext() {
    return new RequestContext(
        onlyofficeIntegrationProperties.getDefaultTenantId(),
        onlyofficeIntegrationProperties.getDefaultSourceSystem(),
        onlyofficeIntegrationProperties.getDefaultUser(),
        onlyofficeIntegrationProperties.getDefaultUserName()
    );
  }

  private String contentTypeFor(String filename) {
    return MediaTypeFactory.getMediaType(filename)
        .orElse(MediaType.APPLICATION_OCTET_STREAM)
        .toString();
  }

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

  private void addZipEntry(ZipOutputStream zipOutputStream, String name, String body) throws IOException {
    zipOutputStream.putNextEntry(new ZipEntry(name));
    zipOutputStream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    zipOutputStream.closeEntry();
  }

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
