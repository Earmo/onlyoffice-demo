package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.model.InsertImageRequest;
import com.earmo.onlyoffice.integration.model.InsertImageResponse;
import com.earmo.onlyoffice.integration.model.OnlyofficeCallbackRequest;
import com.earmo.onlyoffice.integration.model.RemoteImageResource;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.OnlyofficeConfigService;
import com.earmo.onlyoffice.integration.service.OnlyofficeImageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
 *
 * <p>这些接口属于“编辑运行态协议层”，主要服务于：
 * 1. 浏览器在打开编辑器前获取 editor config；
 * 2. ONLYOFFICE Docs 在编辑过程中拉取文件、提交 callback；
 * 3. 前端调用图片插入、保存状态查询等运行时能力。
 */
@Tag(name = "文档运行时接口", description = "提供 ONLYOFFICE 编辑器配置、文件下载、回调和图片代理能力。")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final OnlyofficeConfigService onlyofficeConfigService;
  private final DocumentStorageService documentStorageService;
  private final OnlyofficeImageService onlyofficeImageService;
  private final DocumentStatusService documentStatusService;
  private final AccessAuditService accessAuditService;
  private final AccessContextResolver accessContextResolver;
  private final OnlyofficeJwtService onlyofficeJwtService;

  @GetMapping("/{documentId}/editor-config")
  @Operation(summary = "获取编辑器配置", description = "根据内部 documentId 生成 ONLYOFFICE 可直接消费的 editor config。")
  public EditorConfigResponse editorConfig(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      @Parameter(description = "是否只读打开。", example = "false")
      @RequestParam(defaultValue = "false") boolean readonly,
      HttpServletRequest request
  ) throws IOException {
    AccessContext accessContext = accessContextResolver.resolve(request);
    // 预览和编辑从 Phase 9 开始走两条不同语义：
    // - 只读预览只刷新打开时间，不建立活跃编辑会话；
    // - 编辑工作台则显式建立当前用户的编辑会话。
    if (readonly) {
      documentStatusService.initialize(documentId);
    } else {
      documentStatusService.openEditingSession(documentId, accessContext);
    }
    accessAuditService.recordEditorConfigRequested(documentId, accessContext);
    return onlyofficeConfigService.buildEditorConfig(documentId, readonly, accessContext, request);
  }

  @PostMapping("/{documentId}/editing-sessions/close")
  @Operation(summary = "结束编辑会话", description = "在返回列表、切换文档或离开编辑页时显式结束当前用户的编辑会话。")
  public DocumentSaveStatusResponse closeEditingSession(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return documentStatusService.closeEditingSession(documentId, accessContext);
  }

  @GetMapping("/{documentId}/save-status")
  @Operation(summary = "查询保存状态", description = "返回文档最近一次 callback 和保存回写状态。")
  public DocumentSaveStatusResponse saveStatus(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId
  ) {
    return documentStatusService.getStatus(documentId);
  }

  @GetMapping("/{documentId}/file")
  @Operation(summary = "下载文档文件", description = "给 ONLYOFFICE 或浏览器返回当前文档的文件内容。")
  public ResponseEntity<ByteArrayResource> file(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId
  ) throws IOException {
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
  @Operation(summary = "生成插图参数", description = "把远程图片地址转换成 ONLYOFFICE insertImage 所需参数。")
  public InsertImageResponse insertImage(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      @Valid @RequestBody InsertImageRequest request
  ) {
    return onlyofficeImageService.buildInsertImageResponse(documentId, request.sourceUrl());
  }

  @GetMapping("/{documentId}/images/proxy")
  @Operation(summary = "代理远程图片", description = "下载远程图片并以当前服务地址重新暴露给前端或 ONLYOFFICE。")
  public ResponseEntity<ByteArrayResource> proxyImage(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      @Parameter(description = "远程图片地址。", example = "https://example.com/logo.png")
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
  @Operation(summary = "处理 ONLYOFFICE 回调", description = "接收 ONLYOFFICE callback，并在需要时下载最新文件回写到存储。")
  public Map<String, Integer> callback(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      @RequestBody OnlyofficeCallbackRequest request,
      HttpServletRequest httpServletRequest
  ) throws IOException {
    try {
      onlyofficeJwtService.verifyCallbackRequest(httpServletRequest);
    } catch (IllegalArgumentException ex) {
      documentStatusService.recordCallbackRejected(documentId, ex.getMessage());
      accessAuditService.recordCallbackRejected(documentId, ex.getMessage());
      throw ex;
    }

    Integer status = request.status();
    // 第一步先记录 callback 已到达，哪怕后续下载或保存失败，也能在状态接口里看见这次回调轨迹。
    documentStatusService.recordCallbackReceived(documentId, status);
    accessAuditService.recordCallbackReceived(documentId, status);
    if (status != null && (status == 2 || status == 6)) {
      try {
        // 只有在 ONLYOFFICE 告知文档可持久化时，才真正拉取最新文件并覆盖存储内容。
        documentStorageService.saveCallbackDocument(documentId, request.url());
        documentStatusService.recordSaveSucceeded(documentId, status);
      } catch (IOException ex) {
        // 一旦保存失败，除了抛异常给调用方，还要把失败原因写回状态中心，便于前端展示和排查。
        documentStatusService.recordSaveFailed(documentId, status, ex.getMessage());
        throw ex;
      }
    }
    return Map.of("error", 0);
  }
}


