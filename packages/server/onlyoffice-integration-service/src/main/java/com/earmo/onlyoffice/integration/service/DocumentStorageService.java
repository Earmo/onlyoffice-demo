package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 负责文档文件内容的创建、读取和保存。
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

  private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;
  private final DocumentMetadataService documentMetadataService;
  private final RestClient.Builder restClientBuilder;

  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  private final RestClient restClient = buildRestClient();

  public StoredDocument ensureBootstrapDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    if (documentMetadataService.findDocument(documentId).isPresent()) {
      return getRequiredDocument(documentId);
    }

    RequestContext requestContext = defaultRequestContext();
    String title = documentId + "." + DEFAULT_EXTENSION;
    String storageKey = buildStorageKey(documentId, DEFAULT_EXTENSION);
    Path path = resolveStoragePath(storageKey);
    createBootstrapDocxIfMissing(path);

    DocumentMetadataEntity entity = documentMetadataService.createDocument(
        documentId,
        title,
        DEFAULT_EXTENSION,
        resolveDocumentType(DEFAULT_EXTENSION),
        storageKey,
        requestContext,
        null
    );
    return toStoredDocument(entity, path);
  }

  public StoredDocument getRequiredDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    DocumentMetadataEntity entity = documentMetadataService.requireDocument(documentId);
    Path path = resolveStoragePath(entity.getStorageKey());
    if (!Files.exists(path)) {
      throw new IOException("文档内容不存在：" + entity.getStorageKey());
    }
    return toStoredDocument(entity, path);
  }

  public byte[] readDocument(String rawDocumentId) throws IOException {
    return Files.readAllBytes(getRequiredDocument(rawDocumentId).path());
  }

  public void saveCallbackDocument(String rawDocumentId, String downloadUrl) throws IOException {
    if (!StringUtils.hasText(downloadUrl)) {
      return;
    }

    StoredDocument storedDocument = getRequiredDocument(rawDocumentId);
    byte[] latestFile = getRestClient().get()
        .uri(downloadUrl)
        .retrieve()
        .body(byte[].class);

    if (latestFile == null || latestFile.length == 0) {
      throw new IOException("ONLYOFFICE callback did not return file bytes.");
    }

    Files.write(
        storedDocument.path(),
        latestFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );
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
    String storageKey = buildStorageKey(documentId, extension);
    Path path = resolveStoragePath(storageKey);

    Files.createDirectories(path.getParent());
    Files.write(path, body, StandardOpenOption.CREATE_NEW);
    DocumentMetadataEntity entity = documentMetadataService.createDocument(
        documentId,
        documentId + "." + extension,
        extension,
        resolveDocumentType(extension),
        storageKey,
        requestContext,
        null
    );
    return toStoredDocument(entity, path);
  }

  public StoredDocument importRemoteDocument(String sourceUrl) throws IOException {
    return importRemoteDocument(sourceUrl, defaultRequestContext());
  }

  public StoredDocument importRemoteDocument(String sourceUrl, RequestContext requestContext) throws IOException {
    URI remoteUri = parseAndValidateRemoteUrl(sourceUrl);
    String originalFilename = extractRemoteFilename(remoteUri);
    byte[] body = getRestClient().get()
        .uri(remoteUri)
        .retrieve()
        .body(byte[].class);

    if (body == null || body.length == 0) {
      throw new IOException("远程文档下载失败，响应内容为空。");
    }

    return storeUploadedDocument(originalFilename, body, requestContext);
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
    String storageKey = buildStorageKey(documentId, extension);
    Path path = resolveStoragePath(storageKey);

    createBootstrapDocxIfMissing(path);
    DocumentMetadataEntity entity = documentMetadataService.createDocument(
        documentId,
        title,
        extension,
        resolveDocumentType(extension),
        storageKey,
        requestContext,
        externalDocumentId
    );
    return toStoredDocument(entity, path);
  }

  /**
   * 通过懒加载方式初始化 RestClient，避免每次请求都重复 build，也避免为此保留样板构造器。
   */
  private RestClient buildRestClient() {
    return restClientBuilder.build();
  }

  private StoredDocument toStoredDocument(DocumentMetadataEntity entity, Path path) throws IOException {
    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
    return documentMetadataService.toStoredDocument(entity, path, lastModified);
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

  private Path ensureStorageRoot() throws IOException {
    Path root = onlyofficeIntegrationProperties.getStorageRoot();
    Files.createDirectories(root);
    return root;
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
    if (!StringUtils.hasText(sourceUrl)) {
      throw new IllegalArgumentException("网络文档地址不能为空。");
    }

    URI uri = URI.create(sourceUrl.trim());
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
      throw new IllegalArgumentException("网络文档地址必须是完整的 http/https URL。");
    }

    String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
    if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
      throw new IllegalArgumentException("当前只支持导入 http/https 网络文档。");
    }

    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (normalizedHost.equals("localhost") || normalizedHost.equals("127.0.0.1") || normalizedHost.equals("::1")) {
      throw new IllegalArgumentException("为了避免本地回环地址被滥用，不支持 localhost 文档地址。");
    }

    return uri;
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

  private String buildStorageKey(String documentId, String extension) {
    return "documents/" + documentId + "." + extension;
  }

  private Path resolveStoragePath(String storageKey) throws IOException {
    Path root = ensureStorageRoot().toAbsolutePath().normalize();
    Path resolved = root.resolve(storageKey).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("非法存储路径：" + storageKey);
    }
    return resolved;
  }

  private RequestContext defaultRequestContext() {
    return new RequestContext(
        onlyofficeIntegrationProperties.getDefaultTenantId(),
        onlyofficeIntegrationProperties.getDefaultSourceSystem(),
        onlyofficeIntegrationProperties.getDefaultUser(),
        onlyofficeIntegrationProperties.getDefaultUserName()
    );
  }

  private void createBootstrapDocxIfMissing(Path path) throws IOException {
    if (Files.exists(path)) {
      return;
    }
    Files.createDirectories(path.getParent());
    createBootstrapDocx(path);
  }

  private void createBootstrapDocx(Path path) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
         ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      addZipEntry(zipOutputStream, "[Content_Types].xml", contentTypesXml());
      addZipEntry(zipOutputStream, "_rels/.rels", rootRelationshipsXml());
      addZipEntry(zipOutputStream, "word/document.xml", documentXml());
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


