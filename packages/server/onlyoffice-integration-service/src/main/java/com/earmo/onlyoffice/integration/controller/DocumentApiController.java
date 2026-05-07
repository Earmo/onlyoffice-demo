package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.CurrentAccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.request.CreateDocumentRequest;
import com.earmo.onlyoffice.integration.model.request.DocumentDeleteReq;
import com.earmo.onlyoffice.integration.model.request.DocumentGetReq;
import com.earmo.onlyoffice.integration.model.request.DocumentImportRequest;
import com.earmo.onlyoffice.integration.model.request.DocumentPageReq;
import com.earmo.onlyoffice.integration.model.request.DocumentRecentReq;
import com.earmo.onlyoffice.integration.model.response.DocumentSummaryResponse;
import com.earmo.onlyoffice.integration.model.PageRespVo;
import com.earmo.onlyoffice.integration.model.ResponseDto;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.DocumentMetadataService;
import com.earmo.onlyoffice.integration.exception.DocumentOperationConflictException;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
@Slf4j
public class DocumentApiController extends BaseController {

  private static final int DEFAULT_PAGE_NUMBER = 1;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_RECENT_LIMIT = 3;
  private static final int MAX_RECENT_LIMIT = 10;

  private final DocumentMetadataService documentMetadataService;
  private final DocumentStorageService documentStorageService;
  private final DocumentStatusService documentStatusService;
  private final AccessAuditService accessAuditService;
  private final ObjectMapper objectMapper;

  /**
   * 分页查询当前租户下的文档摘要列表。
   *
   * @param request 分页、筛选和排序请求体；为空时使用默认分页参数。
   * @return 文档摘要分页结果。
   */
  @PostMapping("/page")
  @Operation(summary = "分页查询文档列表", description = "按当前请求上下文中的 tenantId 返回文档摘要分页。")
  public ResponseDto<PageRespVo<DocumentSummaryResponse>> page(@RequestBody(required = false) DocumentPageReq request) {
    DocumentPageReq safeRequest = request == null ? new DocumentPageReq(null, null, null, null, null, null, null, null) : request;
    int safePageNumber = sanitizePageNumber(safeRequest.pageNumber());
    int safePageSize = sanitizePageSize(safeRequest.pageSize());
    DocumentListPage listPage = resolveListPage(
        safeRequest.query(),
        safeRequest.status(),
        safeRequest.sourceSystem(),
        safeRequest.documentType(),
        safeRequest.storage() == null ? "all" : safeRequest.storage(),
        safeRequest.sortDirection() == null ? "desc" : safeRequest.sortDirection(),
        safePageNumber,
        safePageSize
    );
    return successResponseWithData(new PageRespVo<>(
        safePageNumber,
        safePageSize,
        Math.toIntExact(listPage.total()),
        listPage.documents()
    ));
  }

  /**
   * 兼容 text/plain 形式提交的分页查询请求。
   *
   * @param body JSON 字符串形式的分页请求体。
   * @return 文档摘要分页结果。
   */
  @PostMapping(value = "/page", consumes = MediaType.TEXT_PLAIN_VALUE)
  @Operation(summary = "分页查询文档列表", description = "兼容 text/plain 形式提交的 JSON 查询参数。")
  public ResponseDto<PageRespVo<DocumentSummaryResponse>> pageText(@RequestBody(required = false) String body) {
    return page(readTextJsonBody(body, DocumentPageReq.class));
  }

  /**
   * 查询当前租户最近编辑的文档。
   *
   * @param request 最近文档查询请求体；为空时使用默认数量。
   * @return 最近编辑文档摘要列表。
   */
  @PostMapping("/list/recent")
  @Operation(summary = "查询最近编辑文档", description = "返回当前租户最近编辑的活跃文档列表。")
  public ResponseDto<List<DocumentSummaryResponse>> recent(@RequestBody(required = false) DocumentRecentReq request) {
    int safeLimit = sanitizeRecentLimit(request == null ? null : request.limit());
    List<DocumentMetadataEntity> entities = documentMetadataService.listRecentDocuments(currentTenantId(), safeLimit);
    Map<String, Integer> activeEditingCounts = documentStatusService.countActiveEditingSessions(
        entities.stream().map(DocumentMetadataEntity::getDocumentId).toList()
    );
    return successResponseWithData(entities.stream()
        .map(entity -> toSummary(entity, activeEditingCounts.getOrDefault(entity.getDocumentId(), 0)))
        .toList());
  }

  /**
   * 兼容 text/plain 形式提交的最近文档查询请求。
   *
   * @param body JSON 字符串形式的最近文档请求体。
   * @return 最近编辑文档摘要列表。
   */
  @PostMapping(value = "/list/recent", consumes = MediaType.TEXT_PLAIN_VALUE)
  @Operation(summary = "查询最近编辑文档", description = "兼容 text/plain 形式提交的 JSON 查询参数。")
  public ResponseDto<List<DocumentSummaryResponse>> recentText(@RequestBody(required = false) String body) {
    return recent(readTextJsonBody(body, DocumentRecentReq.class));
  }

  /**
   * 查询指定文档的摘要详情。
   *
   * @param request 文档详情查询请求体。
   * @return 文档摘要详情。
   */
  @PostMapping("/detail")
  @Operation(summary = "查询文档详情", description = "根据内部 documentId 查询文档概要信息。")
  public ResponseDto<DocumentSummaryResponse> detail(@Valid @RequestBody DocumentGetReq request) {
    int activeEditors = documentStatusService.countActiveEditingSessions(List.of(request.documentId())).getOrDefault(request.documentId(), 0);
    return successResponseWithData(toSummary(documentMetadataService.requireAccessibleDocument(request.documentId()), activeEditors));
  }

  /**
   * 显式创建一个新的原生文档。
   *
   * @param request 创建文档请求体；为空时使用默认标题和系统生成 ID。
   * @return 新建文档摘要。
   * @throws IOException 创建底层文档失败时抛出。
   */
  @PostMapping("/create")
  @Operation(
      summary = "显式创建文档",
      description = "创建一个新的 docx 文档上下文。该接口不会在 open 时隐式 auto-create。",
      responses = {
          @ApiResponse(responseCode = "200", description = "创建成功"),
          @ApiResponse(
              responseCode = "400",
              description = "参数不合法",
              content = @Content(schema = @Schema(implementation = com.earmo.onlyoffice.integration.common.response.ApiErrorResponse.class))
          )
      }
  )
  public ResponseDto<DocumentSummaryResponse> create(@RequestBody(required = false) CreateDocumentRequest request) throws IOException {
    CreateDocumentRequest safeRequest = request == null ? new CreateDocumentRequest(null, null, null) : request;
    StoredDocument storedDocument = documentStorageService.createNativeDocument(
        null,
        safeRequest.title(),
        CurrentAccessContext.toRequestContext(),
        safeRequest.externalDocumentId()
    );
    accessAuditService.recordDocumentCreated(storedDocument.documentId());
    return successResponseWithData(toSummary(storedDocument));
  }

  /**
   * 上传本地文档并建立内部文档上下文。
   *
   * @param file 要上传的文档文件。
   * @return 上传后的文档摘要。
   * @throws IOException 读取或保存文件失败时抛出。
   */
  @PostMapping("/upload")
  @Operation(summary = "上传文档", description = "上传本地文件并建立文档元数据，返回内部 documentId。")
  public ResponseDto<DocumentSummaryResponse> upload(
      @Parameter(description = "要上传的文档文件。")
      @RequestParam("file") MultipartFile file
  ) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("上传文件不能为空。");
    }

    StoredDocument storedDocument = documentStorageService.storeUploadedDocument(
        file.getOriginalFilename(),
        file.getBytes(),
        CurrentAccessContext.toRequestContext()
    );
    accessAuditService.recordDocumentUploaded(storedDocument.documentId());
    return successResponseWithData(toSummary(storedDocument));
  }

  /**
   * 从远程 URL 导入文档。
   *
   * @param request 远程文档导入请求体。
   * @return 导入后的文档摘要。
   * @throws IOException 下载或保存远程文档失败时抛出。
   */
  @PostMapping("/import-remote")
  @Operation(summary = "导入远程文档", description = "从公网 URL 下载文档并建立内部文档上下文。")
  public ResponseDto<DocumentSummaryResponse> importRemote(@Valid @RequestBody DocumentImportRequest request) throws IOException {
    log.info(
        "收到远程文档导入请求：sourceUrl={}, tenantId={}, actorUser={}",
        request.sourceUrl(),
        currentTenantId(),
        currentActorUser()
    );
    StoredDocument storedDocument = documentStorageService.importRemoteDocument(
        request.sourceUrl(),
        CurrentAccessContext.toRequestContext()
    );
    accessAuditService.recordDocumentImported(storedDocument.documentId());
    return successResponseWithData(toSummary(storedDocument));
  }

  /**
   * 逻辑删除指定文档。
   *
   * @param request 文档删除请求体。
   * @return 空成功响应。
   */
  @PostMapping("/delete")
  @Operation(summary = "删除文档", description = "逻辑删除文档并将状态归档。")
  public ResponseDto<Object> delete(@Valid @RequestBody DocumentDeleteReq request) {
    int activeEditors = documentStatusService.countActiveEditingSessions(List.of(request.documentId())).getOrDefault(request.documentId(), 0);
    if (activeEditors > 0) {
      throw new DocumentOperationConflictException("文档仍有活跃编辑会话，暂时不能删除。");
    }
    documentMetadataService.archiveDocument(request.documentId());
    accessAuditService.recordDocumentArchived(request.documentId());
    return successResponse();
  }

  private DocumentSummaryResponse toSummary(
      DocumentMetadataEntity entity,
      int activeEditingCount
  ) {
    boolean storageAvailable = isStorageAvailable(entity);
    String effectiveStatus = activeEditingCount > 0 ? DocumentMetadataService.STATUS_EDITING : entity.getStatus();
    return new DocumentSummaryResponse(
        entity.getDocumentId(),
        entity.getTitle(),
        entity.getFileType(),
        entity.getDocumentType(),
        effectiveStatus,
        entity.getTenantId(),
        entity.getOwnerUser(),
        currentActorUser(),
        currentActorName(),
        entity.getSourceSystem(),
        entity.getExternalDocumentId(),
        storageAvailable,
        entity.getUpdatedTime(),
        entity.getLastOpenedTime(),
        entity.getLastSavedTime()
    );
  }

  /**
   * 将新建或上传后的存储文档投影成前端摘要。
   *
   * @param storedDocument 存储服务返回的文档对象。
   * @return 文档摘要响应。
   */
  private DocumentSummaryResponse toSummary(StoredDocument storedDocument) {
    return new DocumentSummaryResponse(
        storedDocument.documentId(),
        storedDocument.title(),
        storedDocument.fileType(),
        storedDocument.documentType(),
        storedDocument.status(),
        storedDocument.tenantId(),
        storedDocument.ownerUser(),
        currentActorUser(),
        currentActorName(),
        storedDocument.sourceSystem(),
        storedDocument.externalDocumentId(),
        true,
        storedDocument.lastModified(),
        storedDocument.lastOpenedTime(),
        storedDocument.lastSavedTime()
    );
  }

  /**
   * 列表和详情接口只投影“对象当前是否可读”，避免异常文档被静默隐藏。
   *
   * <p>如果底层存储探测本身抛错，这里也按不可用处理；真正打开文件或生成 editor-config 时，
   * 仍由业务接口返回明确错误，而不是在摘要接口里尝试自动修复。
   *
   * @param entity 文档元数据实体。
   * @return true 表示底层对象当前可读。
   */
  private boolean isStorageAvailable(DocumentMetadataEntity entity) {
    try {
      return documentStorageService.exists(entity);
    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * 判断文档摘要是否匹配存储可用性筛选条件。
   *
   * @param summary 文档摘要。
   * @param storage 存储筛选值，支持 all、available、unavailable。
   * @return true 表示当前摘要应保留在列表中。
   */
  private boolean matchesStorage(DocumentSummaryResponse summary, String storage) {
    return switch (storage == null ? "all" : storage.toLowerCase()) {
      case "available" -> summary.storageAvailable();
      case "unavailable" -> !summary.storageAvailable();
      default -> true;
    };
  }

  /**
   * 解析带筛选和分页的文档列表。
   *
   * @param query 标题或文档标识关键词。
   * @param status 文档状态筛选。
   * @param sourceSystem 来源系统筛选。
   * @param documentType 文档类型筛选。
   * @param storage 存储可用性筛选。
   * @param sortDirection 排序方向。
   * @param pageNumber 页码。
   * @param pageSize 每页条数。
   * @return 分页后的列表数据。
   */
  private DocumentListPage resolveListPage(
      String query,
      String status,
      String sourceSystem,
      String documentType,
      String storage,
      String sortDirection,
      int pageNumber,
      int pageSize
  ) {
    String tenantId = currentTenantId();
    if ("all".equalsIgnoreCase(storage)) {
      Page<DocumentMetadataEntity> entityPage = documentMetadataService.listDocumentPage(
          tenantId,
          query,
          status,
          sourceSystem,
          documentType,
          sortDirection,
          pageNumber,
          pageSize
      );
      Map<String, Integer> activeEditingCounts = documentStatusService.countActiveEditingSessions(
          entityPage.getRecords().stream().map(DocumentMetadataEntity::getDocumentId).toList()
      );
      List<DocumentSummaryResponse> documents = entityPage.getRecords().stream()
          .map(entity -> toSummary(entity, activeEditingCounts.getOrDefault(entity.getDocumentId(), 0)))
          .toList();
      return new DocumentListPage(documents, entityPage.getTotalRow(), entityPage.getTotalPage());
    }

    List<DocumentMetadataEntity> entities = documentMetadataService.listDocuments(
        tenantId,
        query,
        status,
        sourceSystem,
        documentType,
        sortDirection
    );
    Map<String, Integer> activeEditingCounts = documentStatusService.countActiveEditingSessions(
        entities.stream().map(DocumentMetadataEntity::getDocumentId).toList()
    );
    List<DocumentSummaryResponse> filteredDocuments = entities.stream()
        .map(entity -> toSummary(entity, activeEditingCounts.getOrDefault(entity.getDocumentId(), 0)))
        .filter(summary -> matchesStorage(summary, storage))
        .toList();

    long total = filteredDocuments.size();
    long totalPages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    int fromIndex = Math.min((pageNumber - 1) * pageSize, filteredDocuments.size());
    int toIndex = Math.min(fromIndex + pageSize, filteredDocuments.size());
    return new DocumentListPage(filteredDocuments.subList(fromIndex, toIndex), total, totalPages);
  }

  /**
   * 规整页码参数。
   *
   * @param pageNumber 原始页码。
   * @return 可用于查询的安全页码。
   */
  private int sanitizePageNumber(Integer pageNumber) {
    if (pageNumber == null || pageNumber < 1) {
      return DEFAULT_PAGE_NUMBER;
    }
    return pageNumber;
  }

  /**
   * 规整每页条数参数。
   *
   * @param pageSize 原始每页条数。
   * @return 可用于查询的安全每页条数。
   */
  private int sanitizePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  /**
   * 规整最近文档数量参数。
   *
   * @param limit 原始数量限制。
   * @return 可用于查询的安全数量限制。
   */
  private int sanitizeRecentLimit(Integer limit) {
    if (limit == null || limit < 1) {
      return DEFAULT_RECENT_LIMIT;
    }
    return Math.min(limit, MAX_RECENT_LIMIT);
  }

  private <T> T readTextJsonBody(String body, Class<T> type) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, type);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("请求参数格式错误：" + ex.getOriginalMessage());
    }
  }

  private String currentTenantId() {
    return CurrentAccessContext.tenantId();
  }

  private String currentActorUser() {
    return CurrentAccessContext.actorUser();
  }

  private String currentActorName() {
    return CurrentAccessContext.actorName();
  }

  private record DocumentListPage(
      List<DocumentSummaryResponse> documents,
      long total,
      long totalPages
  ) {
  }
}
