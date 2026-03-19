package com.earmo.onlyoffice.demo.web;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.demo.model.EditorConfigResponse;
import com.earmo.onlyoffice.demo.model.InsertImageRequest;
import com.earmo.onlyoffice.demo.model.InsertImageResponse;
import com.earmo.onlyoffice.demo.model.OnlyofficeCallbackRequest;
import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.model.RemoteImageResource;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import com.earmo.onlyoffice.demo.service.DocumentStatusService;
import com.earmo.onlyoffice.demo.service.DocumentStorageService;
import com.earmo.onlyoffice.demo.service.OnlyofficeConfigService;
import com.earmo.onlyoffice.demo.service.OnlyofficeImageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
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

/**
 * 暴露给前端和 ONLYOFFICE Docs 的运行时接口。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

  private final OnlyofficeConfigService onlyofficeConfigService;
  private final DocumentStorageService documentStorageService;
  private final OnlyofficeImageService onlyofficeImageService;
  private final DocumentStatusService documentStatusService;
  private final RequestContextResolver requestContextResolver;

  public DocumentController(
      OnlyofficeConfigService onlyofficeConfigService,
      DocumentStorageService documentStorageService,
      OnlyofficeImageService onlyofficeImageService,
      DocumentStatusService documentStatusService,
      RequestContextResolver requestContextResolver
  ) {
    this.onlyofficeConfigService = onlyofficeConfigService;
    this.documentStorageService = documentStorageService;
    this.onlyofficeImageService = onlyofficeImageService;
    this.documentStatusService = documentStatusService;
    this.requestContextResolver = requestContextResolver;
  }

  @GetMapping("/{documentId}/editor-config")
  public EditorConfigResponse editorConfig(
      @PathVariable String documentId,
      @RequestParam(defaultValue = "false") boolean readonly,
      HttpServletRequest request
  ) throws IOException {
    RequestContext requestContext = requestContextResolver.resolve(request);
    documentStatusService.initialize(documentId);
    return onlyofficeConfigService.buildEditorConfig(documentId, readonly, requestContext, request);
  }

  @GetMapping("/{documentId}/save-status")
  public DocumentSaveStatusResponse saveStatus(@PathVariable String documentId) {
    return documentStatusService.getStatus(documentId);
  }

  @GetMapping("/{documentId}/file")
  public ResponseEntity<ByteArrayResource> file(@PathVariable String documentId) throws IOException {
    StoredDocument storedDocument = documentStorageService.getRequiredDocument(documentId);
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

  @PostMapping("/{documentId}/images/insert")
  public InsertImageResponse insertImage(
      @PathVariable String documentId,
      @Valid @RequestBody InsertImageRequest request
  ) {
    return onlyofficeImageService.buildInsertImageResponse(documentId, request.sourceUrl());
  }

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

  @PostMapping("/{documentId}/callback")
  public Map<String, Integer> callback(
      @PathVariable String documentId,
      @RequestBody OnlyofficeCallbackRequest request
  ) throws IOException {
    Integer status = request.status();
    documentStatusService.recordCallbackReceived(documentId, status);
    if (status != null && (status == 2 || status == 6)) {
      try {
        documentStorageService.saveCallbackDocument(documentId, request.url());
        documentStatusService.recordSaveSucceeded(documentId, status);
      } catch (IOException ex) {
        documentStatusService.recordSaveFailed(documentId, status, ex.getMessage());
        throw ex;
      }
    }
    return Map.of("error", 0);
  }
}
