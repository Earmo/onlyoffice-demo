package com.earmo.onlyoffice.demo.web;

import com.earmo.onlyoffice.demo.model.DocumentImportRequest;
import com.earmo.onlyoffice.demo.model.DocumentSummaryResponse;
import com.earmo.onlyoffice.demo.model.EditorConfigResponse;
import com.earmo.onlyoffice.demo.model.InsertImageRequest;
import com.earmo.onlyoffice.demo.model.InsertImageResponse;
import com.earmo.onlyoffice.demo.model.OnlyofficeCallbackRequest;
import com.earmo.onlyoffice.demo.model.RemoteImageResource;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import com.earmo.onlyoffice.demo.service.DocumentStorageService;
import com.earmo.onlyoffice.demo.service.OnlyofficeConfigService;
import com.earmo.onlyoffice.demo.service.OnlyofficeImageService;
import java.io.IOException;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 暴露给前端和 ONLYOFFICE Docs 的文档接口。
 *
 * <p>这里把两类调用统一放在一个控制器里：
 * 1. 前端浏览器调用 editor-config 获取初始化参数；
 * 2. ONLYOFFICE Docs 调用 file 和 callback 完成下载与保存。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

  private final OnlyofficeConfigService onlyofficeConfigService;
  private final DocumentStorageService documentStorageService;
  private final OnlyofficeImageService onlyofficeImageService;

  public DocumentController(
      OnlyofficeConfigService onlyofficeConfigService,
      DocumentStorageService documentStorageService,
      OnlyofficeImageService onlyofficeImageService
  ) {
    this.onlyofficeConfigService = onlyofficeConfigService;
    this.documentStorageService = documentStorageService;
    this.onlyofficeImageService = onlyofficeImageService;
  }

  /**
   * 返回前端初始化 ONLYOFFICE 编辑器所需的配置。
   */
  @GetMapping("/{documentId}/editor-config")
  public EditorConfigResponse editorConfig(
      @PathVariable String documentId,
      @RequestParam(defaultValue = "false") boolean readonly
  ) throws IOException {
    return onlyofficeConfigService.buildEditorConfig(documentId, readonly);
  }

  /**
   * 向 ONLYOFFICE Docs 提供文档原始字节流。
   *
   * <p>当编辑器初始化时，ONLYOFFICE 服务端会访问这个地址拉取源文件。
   */
  @GetMapping("/{documentId}/file")
  public ResponseEntity<ByteArrayResource> file(@PathVariable String documentId) throws IOException {
    StoredDocument storedDocument = documentStorageService.getOrCreateDocument(documentId);
    byte[] body = documentStorageService.readDocument(documentId);
    MediaType mediaType = MediaTypeFactory.getMediaType(storedDocument.title())
        .orElse(MediaType.APPLICATION_OCTET_STREAM);

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(storedDocument.title()).build().toString()
        )
        .body(new ByteArrayResource(body));
  }

  /**
   * 上传本地文档并返回新文档信息。
   */
  @PostMapping("/upload")
  public DocumentSummaryResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("上传文件不能为空。");
    }

    StoredDocument storedDocument = documentStorageService.storeUploadedDocument(
        file.getOriginalFilename(),
        file.getBytes()
    );
    return toSummary(storedDocument);
  }

  /**
   * 导入网络文档并返回新文档信息。
   */
  @PostMapping("/import-remote")
  public DocumentSummaryResponse importRemote(@Valid @RequestBody DocumentImportRequest request) throws IOException {
    return toSummary(documentStorageService.importRemoteDocument(request.sourceUrl()));
  }

  /**
   * 生成前端调用 docEditor.insertImage(...) 所需的签名参数。
   */
  @PostMapping("/{documentId}/images/insert")
  public InsertImageResponse insertImage(
      @PathVariable String documentId,
      @Valid @RequestBody InsertImageRequest request
  ) {
    return onlyofficeImageService.buildInsertImageResponse(documentId, request.sourceUrl());
  }

  /**
   * 代理远程图片给 ONLYOFFICE Docs 下载。
   */
  @GetMapping("/{documentId}/images/proxy")
  public ResponseEntity<ByteArrayResource> proxyImage(
      @PathVariable String documentId,
      @RequestParam String sourceUrl
  ) throws IOException {
    RemoteImageResource resource = onlyofficeImageService.proxyRemoteImage(sourceUrl);
    return ResponseEntity.ok()
        .contentType(resource.mediaType())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(resource.filename()).build().toString()
        )
        .body(new ByteArrayResource(resource.body()));
  }

  /**
   * 接收 ONLYOFFICE 的保存回调。
   *
   * <p>示例里只处理 status=2 和 status=6：
   * 这两个状态都代表已经拿到可下载的最新文件，可以安全覆盖本地文档。
   */
  @PostMapping("/{documentId}/callback")
  public Map<String, Integer> callback(
      @PathVariable String documentId,
      @RequestBody OnlyofficeCallbackRequest request
  ) throws IOException {
    Integer status = request.status();
    if (status != null && (status == 2 || status == 6)) {
      // 只有在文档确实可持久化时才去下载新文件，避免对其他状态做无意义请求。
      documentStorageService.saveCallbackDocument(documentId, request.url());
    }
    // ONLYOFFICE 约定成功响应返回 {"error": 0}。
    return Map.of("error", 0);
  }

  private DocumentSummaryResponse toSummary(StoredDocument storedDocument) {
    return new DocumentSummaryResponse(
        storedDocument.documentId(),
        storedDocument.title(),
        storedDocument.fileType(),
        storedDocument.documentType()
    );
  }
}
