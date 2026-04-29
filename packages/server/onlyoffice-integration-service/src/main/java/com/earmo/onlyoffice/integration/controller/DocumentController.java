package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.model.EditorConfigResponse;
import com.earmo.onlyoffice.integration.model.InsertImageRequest;
import com.earmo.onlyoffice.integration.model.InsertImageResponse;
import com.earmo.onlyoffice.integration.model.OnlyofficeCallbackRequest;
import com.earmo.onlyoffice.integration.model.RemoteImageResource;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.service.DocumentRuntimeEventStreamService;
import com.earmo.onlyoffice.integration.service.DocumentStatusService;
import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import com.earmo.onlyoffice.integration.service.AccessAuditService;
import com.earmo.onlyoffice.integration.service.OnlyofficeCommandService;
import com.earmo.onlyoffice.integration.service.OnlyofficeConfigService;
import com.earmo.onlyoffice.integration.service.OnlyofficeImageService;
import com.earmo.onlyoffice.integration.service.OnlyofficeJwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
  private final DocumentRuntimeEventStreamService runtimeEventStreamService;
  private final AccessAuditService accessAuditService;
  private final AccessContextResolver accessContextResolver;
  private final OnlyofficeJwtService onlyofficeJwtService;
  private final OnlyofficeCommandService onlyofficeCommandService;

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
  @Operation(summary = "结束编辑会话", description = "在返回列表、切换文档或离开编辑页时显式结束当前用户的编辑会话。" +
          "前端应在调用此接口前先触发保存并等待本次显式保存完成；若随后 ONLYOFFICE 继续补发关闭类 callback，后端会按活跃编辑会话重新收口列表状态。")
  public DocumentSaveStatusResponse closeEditingSession(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return documentStatusService.closeEditingSession(documentId, accessContext);
  }

  @PostMapping("/{documentId}/save")
  @Operation(summary = "保存当前编辑内容", description = "通过 ONLYOFFICE Command Service 触发 forcesave，并等待本次 callback 回写完成后返回最新保存状态。")
  public DocumentSaveStatusResponse saveDocument(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId
  ) {
    onlyofficeCommandService.forceSaveAndAwait(documentId, 8000L);
    return documentStatusService.getStatus(documentId);
  }

  @GetMapping("/{documentId}/save-status")
  @Operation(summary = "查询保存状态", description = "返回文档最近一次 callback 和保存回写状态。该接口主要服务编辑页运行态展示，" +
          "不作为列表页是否仍处于 editing 的唯一判据。")
  public DocumentSaveStatusResponse saveStatus(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId
  ) {
    return documentStatusService.getStatus(documentId);
  }

  @GetMapping(path = "/{documentId}/runtime-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "订阅文档运行态事件流", description = "返回文档级 SSE 事件流，首帧包含当前 save-status 快照。")
  public SseEmitter runtimeEvents(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    // Phase 14.1 的后端入口流程：
    // 1. 先从请求头里解析 access context，拿到当前 actor/tenant。
    //    这里不能偷懒，因为后面的 editing session 续期依赖 actorUser。
    // 2. 在真正打开 SSE 之前，先补一次 touchEditingSession。
    //    healthy runtime-events 是编辑会话存活主路径；这次 touch 覆盖连接建立瞬间，
    //    后续由 SSE keepalive 成功发送后的 livenessTouch 续期。
    // 3. 再读取当前 save-status，作为 SSE 的首帧快照。
    //    这样前端不需要先等下一次 callback/save 才知道当前文档状态。
    // 4. 最后把 livenessTouch 交给 SSE service。
    //    后续每次 keepalive 发送成功才 touch editing session，
    //    让“流还活着”和“当前用户仍在编辑”这两个事实保持一致。
    documentStatusService.touchEditingSession(documentId, accessContext);
    DocumentSaveStatusResponse initialStatus = documentStatusService.getStatus(documentId);
    return runtimeEventStreamService.open(
        documentId,
        accessContext,
        initialStatus,
        () -> documentStatusService.touchEditingSession(documentId, accessContext)
    );
  }

  @GetMapping("/{documentId}/file")
  @Operation(summary = "下载文档文件", description = "给 ONLYOFFICE 或浏览器返回当前文档的文件内容。")
  public ResponseEntity<ByteArrayResource> file(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId
  ) throws IOException {
    return buildFileResponse(documentId);
  }

  @GetMapping("/{documentId}/file.{extension}")
  @Operation(summary = "下载文档文件", description = "给 ONLYOFFICE 或浏览器返回当前文档的文件内容。")
  public ResponseEntity<ByteArrayResource> fileWithExtension(
      @Parameter(description = "文档内部主键。", example = "demo")
      @PathVariable String documentId,
      @Parameter(description = "文件扩展名，仅用于让下载 URL 自带类型语义。", example = "docx")
      @PathVariable String extension
  ) throws IOException {
    return buildFileResponse(documentId);
  }

  private ResponseEntity<ByteArrayResource> buildFileResponse(String documentId) throws IOException {
    StoredDocument storedDocument = documentStorageService.getRequiredDocument(documentId);
    byte[] body = documentStorageService.readDocument(documentId);
    MediaType mediaType = MediaTypeFactory.getMediaType(storedDocument.title())
        .orElse(MediaType.APPLICATION_OCTET_STREAM);

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(storedDocument.title(), StandardCharsets.UTF_8).build().toString()
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
        documentStorageService.saveCallbackDocument(documentId, request.url(), request.filetype());
        documentStatusService.recordSaveSucceeded(documentId, status);
        // 唤醒正在 forceSaveAndAwait 中等待的线程（如有）。
        onlyofficeCommandService.notifySaveCompleted(documentId);
      } catch (IOException ex) {
        // 一旦保存失败，除了抛异常给调用方，还要把失败原因写回状态中心，便于前端展示和排查。
        documentStatusService.recordSaveFailed(documentId, status, ex.getMessage());
        throw ex;
      }
    }
    return Map.of("error", 0);
  }
}
