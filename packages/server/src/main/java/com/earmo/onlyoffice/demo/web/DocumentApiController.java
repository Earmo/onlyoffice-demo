package com.earmo.onlyoffice.demo.web;

import com.earmo.onlyoffice.demo.model.CreateDocumentRequest;
import com.earmo.onlyoffice.demo.model.DocumentImportRequest;
import com.earmo.onlyoffice.demo.model.DocumentListResponse;
import com.earmo.onlyoffice.demo.model.DocumentSummaryResponse;
import com.earmo.onlyoffice.demo.model.RequestContext;
import com.earmo.onlyoffice.demo.model.StoredDocument;
import com.earmo.onlyoffice.demo.persistence.DocumentMetadataEntity;
import com.earmo.onlyoffice.demo.service.DocumentMetadataService;
import com.earmo.onlyoffice.demo.service.DocumentStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对外暴露文档主数据和接入 API。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentApiController {

  private final DocumentMetadataService documentMetadataService;
  private final DocumentStorageService documentStorageService;
  private final RequestContextResolver requestContextResolver;

  public DocumentApiController(
      DocumentMetadataService documentMetadataService,
      DocumentStorageService documentStorageService,
      RequestContextResolver requestContextResolver
  ) {
    this.documentMetadataService = documentMetadataService;
    this.documentStorageService = documentStorageService;
    this.requestContextResolver = requestContextResolver;
  }

  @GetMapping
  public DocumentListResponse list(HttpServletRequest request) {
    RequestContext requestContext = requestContextResolver.resolve(request);
    List<DocumentSummaryResponse> documents = documentMetadataService.listDocuments(requestContext.tenantId()).stream()
        .map(this::toSummary)
        .toList();
    return new DocumentListResponse(documents);
  }

  @GetMapping("/{documentId}")
  public DocumentSummaryResponse detail(@PathVariable String documentId) {
    return toSummary(documentMetadataService.requireDocument(documentId));
  }

  @PostMapping
  public DocumentSummaryResponse create(
      @RequestBody(required = false) CreateDocumentRequest request,
      HttpServletRequest httpServletRequest
  ) throws IOException {
    RequestContext requestContext = requestContextResolver.resolve(httpServletRequest);
    CreateDocumentRequest safeRequest = request == null ? new CreateDocumentRequest(null, null, null) : request;
    StoredDocument storedDocument = documentStorageService.createNativeDocument(
        safeRequest.documentId(),
        safeRequest.title(),
        requestContext,
        safeRequest.externalDocumentId()
    );
    return toSummary(storedDocument);
  }

  @PostMapping("/upload")
  public DocumentSummaryResponse upload(
      @RequestParam("file") MultipartFile file,
      HttpServletRequest request
  ) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("上传文件不能为空。");
    }

    RequestContext requestContext = requestContextResolver.resolve(request);
    StoredDocument storedDocument = documentStorageService.storeUploadedDocument(
        file.getOriginalFilename(),
        file.getBytes(),
        requestContext
    );
    return toSummary(storedDocument);
  }

  @PostMapping("/import-remote")
  public DocumentSummaryResponse importRemote(
      @Valid @RequestBody DocumentImportRequest request,
      HttpServletRequest httpServletRequest
  ) throws IOException {
    RequestContext requestContext = requestContextResolver.resolve(httpServletRequest);
    return toSummary(documentStorageService.importRemoteDocument(request.sourceUrl(), requestContext));
  }

  private DocumentSummaryResponse toSummary(DocumentMetadataEntity entity) {
    return new DocumentSummaryResponse(
        entity.getDocumentId(),
        entity.getTitle(),
        entity.getFileType(),
        entity.getDocumentType(),
        entity.getStatus(),
        entity.getTenantId(),
        entity.getOwnerUserId(),
        entity.getSourceSystem(),
        entity.getExternalDocumentId(),
        entity.getLastOpenedAt(),
        entity.getLastSavedAt()
    );
  }

  private DocumentSummaryResponse toSummary(StoredDocument storedDocument) {
    return new DocumentSummaryResponse(
        storedDocument.documentId(),
        storedDocument.title(),
        storedDocument.fileType(),
        storedDocument.documentType(),
        storedDocument.status(),
        storedDocument.tenantId(),
        storedDocument.ownerUserId(),
        storedDocument.sourceSystem(),
        storedDocument.externalDocumentId(),
        storedDocument.lastOpenedAt(),
        storedDocument.lastSavedAt()
    );
  }
}
