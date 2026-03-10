package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.config.DemoProperties;
import com.earmo.onlyoffice.demo.model.StoredDocument;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 负责本地文档的创建、读取和保存。
 *
 * <p>这是示例项目里最接近“持久化层”的一层，职责只有两类：
 * 1. 如果文档不存在，就生成一个最小可编辑的 docx；
 * 2. 当 ONLYOFFICE 回调告知文档已可保存时，把最新文件覆盖到本地。
 */
@Service
public class DocumentStorageService {

  /**
   * 默认文档扩展名。
   *
   * <p>这里固定用 docx，而不是 txt，是因为 docx 更符合 ONLYOFFICE 的主编辑场景。
   */
  private static final String DEFAULT_EXTENSION = "docx";
  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
      "doc", "docx", "odt", "rtf", "txt",
      "xls", "xlsx", "ods", "csv",
      "ppt", "pptx", "odp",
      "pdf"
  );

  private final DemoProperties demoProperties;
  private final RestClient restClient;

  public DocumentStorageService(DemoProperties demoProperties, RestClient.Builder restClientBuilder) {
    this.demoProperties = demoProperties;
    this.restClient = restClientBuilder.build();
  }

  /**
   * 获取本地文档；如果不存在则现场创建。
   *
   * <p>这个示例没有文档元数据表，因此直接把 documentId 映射为本地文件名。
   */
  public StoredDocument getOrCreateDocument(String rawDocumentId) throws IOException {
    String documentId = sanitizeDocumentId(rawDocumentId);
    Path root = ensureStorageRoot();

    Path path = findDocumentPath(documentId);
    if (path == null) {
      path = root.resolve(documentId + "." + DEFAULT_EXTENSION);
      // 首次访问时自动生成一个最小 DOCX，保证前端不用额外上传样例文件。
      createDemoDocx(path);
    }

    return toStoredDocument(documentId, path);
  }

  /**
   * 读取文档原始字节流，供 ONLYOFFICE Docs 下载源文件。
   */
  public byte[] readDocument(String rawDocumentId) throws IOException {
    return Files.readAllBytes(getOrCreateDocument(rawDocumentId).path());
  }

  /**
   * 把 ONLYOFFICE callback 中返回的最新文件覆盖保存到本地。
   *
   * <p>callback 只给下载地址，不直接给文件内容，所以这里需要二次发起 HTTP 请求。
   */
  public void saveCallbackDocument(String rawDocumentId, String downloadUrl) throws IOException {
    if (!StringUtils.hasText(downloadUrl)) {
      // 某些状态下 callback 不会带下载地址，直接忽略即可。
      return;
    }

    StoredDocument storedDocument = getOrCreateDocument(rawDocumentId);
    byte[] latestFile = restClient.get()
        .uri(downloadUrl)
        .retrieve()
        .body(byte[].class);

    if (latestFile == null || latestFile.length == 0) {
      throw new IOException("ONLYOFFICE callback did not return file bytes.");
    }

    // 使用覆盖写入，保持同一个 documentId 始终指向最新版本的文件。
    Files.write(
        storedDocument.path(),
        latestFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );
  }

  /**
   * 保存上传的本地文档，并返回新文档描述。
   */
  public StoredDocument storeUploadedDocument(String originalFilename, byte[] body) throws IOException {
    if (body == null || body.length == 0) {
      throw new IllegalArgumentException("上传文件不能为空。");
    }

    Path root = ensureStorageRoot();
    String extension = requireSupportedExtension(originalFilename);
    String documentId = buildGeneratedDocumentId(stripExtension(originalFilename));
    Path path = root.resolve(documentId + "." + extension);

    Files.write(path, body, StandardOpenOption.CREATE_NEW);
    return toStoredDocument(documentId, path);
  }

  /**
   * 下载网络文档并存入本地。
   */
  public StoredDocument importRemoteDocument(String sourceUrl) throws IOException {
    URI remoteUri = parseAndValidateRemoteUrl(sourceUrl);
    String originalFilename = extractRemoteFilename(remoteUri);
    byte[] body = restClient.get()
        .uri(remoteUri)
        .retrieve()
        .body(byte[].class);

    if (body == null || body.length == 0) {
      throw new IOException("远程文档下载失败，响应内容为空。");
    }

    return storeUploadedDocument(originalFilename, body);
  }

  /**
   * 把文件路径转换成业务层更容易消费的文档对象。
   */
  private StoredDocument toStoredDocument(String documentId, Path path) throws IOException {
    String title = path.getFileName().toString();
    String fileType = getFileExtension(title);
    String documentType = resolveDocumentType(fileType);
    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
    return new StoredDocument(documentId, title, fileType, documentType, path, lastModified);
  }

  /**
   * 根据文件名提取扩展名。
   */
  private String getFileExtension(String filename) {
    int index = filename.lastIndexOf('.');
    if (index < 0 || index == filename.length() - 1) {
      return DEFAULT_EXTENSION;
    }
    return filename.substring(index + 1).toLowerCase(Locale.ROOT);
  }

  /**
   * 把具体扩展名归并成 ONLYOFFICE 识别的 documentType。
   *
   * <p>ONLYOFFICE 前端初始化时要求提供较粗粒度的类型，例如 word/cell/slide/pdf。
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
   * 对外部传入的 documentId 做最小清洗，避免直接参与路径拼接。
   */
  private String sanitizeDocumentId(String rawDocumentId) {
    if (!StringUtils.hasText(rawDocumentId)) {
      return "demo";
    }

    String sanitized = rawDocumentId.trim().replaceAll("[^a-zA-Z0-9_-]", "-");
    return StringUtils.hasText(sanitized) ? sanitized : "demo";
  }

  private Path ensureStorageRoot() throws IOException {
    Path root = demoProperties.getStorageRoot();
    Files.createDirectories(root);
    return root;
  }

  /**
   * 按 documentId 搜索当前真实文件。
   *
   * <p>导入文档后扩展名可能是 xlsx/pptx/pdf，因此不能再假定永远是 docx。
   */
  private Path findDocumentPath(String documentId) throws IOException {
    Path root = ensureStorageRoot();
    try (var stream = Files.list(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(documentId + "."))
          .findFirst()
          .orElse(null);
    }
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
    if ("demo".equals(baseName)) {
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

  /**
   * 生成一个最小可用的 DOCX 文件。
   *
   * <p>DOCX 本质上是 ZIP 包，这里直接写入必要的 OpenXML 文件，
   * 省掉额外依赖，也让示例启动后立即可编辑。
   */
  private void createDemoDocx(Path path) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
         ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      addZipEntry(zipOutputStream, "[Content_Types].xml", contentTypesXml());
      addZipEntry(zipOutputStream, "_rels/.rels", rootRelationshipsXml());
      addZipEntry(zipOutputStream, "word/document.xml", documentXml());
    }
  }

  /**
   * 向 DOCX ZIP 包中写入单个条目。
   */
  private void addZipEntry(ZipOutputStream zipOutputStream, String name, String body) throws IOException {
    zipOutputStream.putNextEntry(new ZipEntry(name));
    zipOutputStream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    zipOutputStream.closeEntry();
  }

  /**
   * DOCX 的内容类型声明文件。
   *
   * <p>告诉 Office/ONLYOFFICE 这个压缩包里各个扩展名和主文档入口分别是什么类型。
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
   * DOCX 根关系定义。
   *
   * <p>通过这个文件把整个文档包的入口指向 word/document.xml。
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
   * 示例文档正文内容。
   *
   * <p>这里只保留最少的段落和页面配置，目标是让文件足够小、结构足够稳定。
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
                <w:t>Spring Boot + Vue + ONLYOFFICE</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:r>
                <w:t>这是最小集成示例生成的演示文档。</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:r>
                <w:t>你可以直接在编辑器里修改内容，然后关闭页面或点击保存。</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:r>
                <w:t>ONLYOFFICE 会通过 callback 把结果回写到 Spring Boot 本地文件。</w:t>
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
