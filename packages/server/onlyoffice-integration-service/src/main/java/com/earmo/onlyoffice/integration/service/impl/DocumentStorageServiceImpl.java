package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.RemoteResourceSecurityService;
import com.earmo.onlyoffice.integration.storage.DocumentStorageStrategy;
import com.earmo.onlyoffice.integration.storage.StorageKeyFactory;
import com.earmo.onlyoffice.integration.storage.StorageProvider;
import com.earmo.onlyoffice.integration.storage.StorageProviderResolver;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import com.mybatisflex.core.keygen.impl.ULIDKeyGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class DocumentStorageServiceImpl implements DocumentStorageService {

  private static final String DEFAULT_EXTENSION = "docx";
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

  @Override
  public StoredDocument getRequiredDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    DocumentMetadataEntity entity = documentMetadataService.requireDocument(documentId);
    DocumentStorageStrategy strategy = resolveStrategy(entity);
    if (!strategy.exists(entity.getStorageKey())) {
      throw new IOException("文档内容不存在：" + entity.getStorageKey());
    }
    return toStoredDocument(entity, strategy.read(entity.getStorageKey()));
  }

  @Override
  public byte[] readDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    DocumentMetadataEntity entity = documentMetadataService.requireDocument(documentId);
    DocumentStorageStrategy strategy = resolveStrategy(entity);
    return strategy.read(entity.getStorageKey()).body();
  }

  /**
   * callback 回写只负责“把最新文件对象覆盖到共享存储”。
   *
   * <p>主表摘要状态、运行事件流和审计事件都在其他服务里更新；
   * 这里保持单一职责，只处理下载最新文件并覆盖对象内容。
   */
  @Override
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

  @Override
  public StoredDocument storeUploadedDocument(String originalFilename, byte[] body) throws IOException {
    return storeUploadedDocument(originalFilename, body, defaultRequestContext());
  }

  /**
   * 上传链路采用“先写对象，再落元数据”的顺序。
   *
   * <p>这样可以避免数据库先成功、对象写入后失败时留下半成品文档。
   * 一旦对象写入成功但元数据插入失败，会尝试 best-effort 删除对象，减少脏数据残留。
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
   */
  @Override
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

  /**
   * 显式创建原生文档时，仍然沿用“对象先写入、主数据后创建”的顺序。
   *
   * <p>这样无论底层用的是 local、minio 还是后续的 cos，行为都保持一致。
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

  @Override
  public boolean exists(DocumentMetadataEntity entity) throws IOException {
    return resolveStrategy(entity).exists(entity.getStorageKey());
  }

  @Override
  public StorageProvider resolveProvider(DocumentMetadataEntity entity) {
    return storageProviderResolver.resolve(entity);
  }

  /**
   * 通过懒加载方式初始化 RestClient，避免每次请求都重复 build。
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

  /**
   * 这里只做 best-effort 补偿，不覆盖原始主异常。
   *
   * <p>如果建档主流程已经失败，补偿删除再失败也不应该吞掉真正导致业务失败的原因。
   */
  private void deleteQuietly(DocumentStorageStrategy strategy, String storageKey) {
    try {
      strategy.delete(storageKey);
    } catch (IOException ignored) {
      // 补偿删除失败只记录为 best-effort，不覆盖主异常。
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

  private String generateDocumentId() {
    return DOCUMENT_ID_GENERATOR.nextMonotonicId();
  }

  private void rollbackGeneratedObjectIfEntityReused(
      DocumentStorageStrategy strategy,
      String generatedStorageKey,
      DocumentMetadataEntity entity
  ) {
    if (!generatedStorageKey.equals(entity.getStorageKey())) {
      deleteQuietly(strategy, generatedStorageKey);
    }
  }

  private String stripExtension(String filename) {
    int index = filename.lastIndexOf('.');
    if (index <= 0) {
      return filename;
    }
    return filename.substring(0, index);
  }

  private String normalizeFilename(String filename) {
    String normalized = StringUtils.getFilename(filename);
    return StringUtils.hasText(normalized) ? normalized : filename;
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

  /**
   * 当系统还没有任何模板文件时，仍然需要生成一个合法可编辑的 docx。
   *
   * <p>这里手工构造最小 OpenXML 包，而不是依赖额外模板文件：
   * 1. `[Content_Types].xml` 声明文档类型；
   * 2. `_rels/.rels` 指向主文档；
   * 3. `word/document.xml` 放入一份可直接打开的初始内容。
   *
   * <p>这样无论部署环境是否预置模板，都能稳定创建 starter 的引导文档。
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
