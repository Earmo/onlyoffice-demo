package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.CreateDocumentRequest;
import com.earmo.onlyoffice.integration.model.DocumentImportRequest;
import com.earmo.onlyoffice.integration.model.DocumentListResponse;
import com.earmo.onlyoffice.integration.model.DocumentSummaryResponse;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "文档主数据接口", description = "提供文档列表、详情、创建、上传和远程导入能力。")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentApiController {

  private final DocumentMetadataService documentMetadataService;
  private final DocumentStorageService documentStorageService;
  private final RequestContextResolver requestContextResolver;

  @GetMapping
  @Operation(summary = "查询文档列表", description = "按当前请求上下文中的 tenantId 返回文档摘要列表。")
  public DocumentListResponse list(HttpServletRequest request) {
    RequestContext requestContext = requestContextResolver.resolve(request);
    List<DocumentSummaryResponse> documents = documentMetadataService.listDocuments(requestContext.tenantId()).stream()
        .map(this::toSummary)
        .toList();
    return new DocumentListResponse(documents);
  }

  @GetMapping("/{documentId}")
  @Operation(summary = "查询文档详情", description = "根据内部 documentId 查询文档概要信息。")
  public DocumentSummaryResponse detail(
      @Parameter(description = "文档内部主键。", example = "sample")
      @PathVariable String documentId
  ) {
    return toSummary(documentMetadataService.requireDocument(documentId));
  }

  @PostMapping
  @Operation(
      summary = "显式创建文档",
      description = "创建一个新的 docx 文档上下文。该接口不会在 open 时隐式 auto-create。",
      responses = {
          @ApiResponse(responseCode = "200", description = "创建成功"),
          @ApiResponse(
              responseCode = "400",
              description = "参数不合法",
              content = @Content(schema = @Schema(implementation = com.earmo.onlyoffice.integration.model.ApiErrorResponse.class))
          )
      }
  )
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
  @Operation(summary = "上传文档", description = "上传本地文件并建立文档元数据，返回内部 documentId。")
  public DocumentSummaryResponse upload(
      @Parameter(description = "要上传的文档文件。")
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
  @Operation(summary = "导入远程文档", description = "从公网 URL 下载文档并建立内部文档上下文。")
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
        entity.getOwnerUser(),
        entity.getSourceSystem(),
        entity.getExternalDocumentId(),
        entity.getLastOpenedTime(),
        entity.getLastSavedTime()
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
        storedDocument.ownerUser(),
        storedDocument.sourceSystem(),
        storedDocument.externalDocumentId(),
        storedDocument.lastOpenedTime(),
        storedDocument.lastSavedTime()
    );
  }
}
