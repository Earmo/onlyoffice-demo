package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.common.exception.BaseException;
import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.CurrentAccessContext;
import com.earmo.onlyoffice.integration.model.ResponseDto;
import com.earmo.onlyoffice.integration.model.llm.CreateLlmSessionRequest;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityReq;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmMessageResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestCancelReq;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestGetReq;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDeleteReq;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDetailResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionGetReq;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionListReq;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionRenameReq;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionSummaryResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.earmo.onlyoffice.integration.model.llm.SetLlmActiveVariantRequest;
import com.earmo.onlyoffice.integration.service.llm.LlmConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController extends BaseController {

  private final LlmConversationService llmConversationService;

  @PostMapping("/get/capability")
  public ResponseDto<LlmCapabilityResponse> capability(@Valid @RequestBody LlmCapabilityReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.getCapability(request.documentId(), accessContext));
  }

  /**
   * 查询指定文档的 AI 能力。
   *
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return AI 能力响应。
   */
  @GetMapping("/capability")
  @Deprecated(forRemoval = false)
  public LlmCapabilityResponse capability(@RequestParam String documentId, HttpServletRequest request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return llmConversationService.getCapability(documentId, accessContext);
  }

  @PostMapping("/list/session")
  public ResponseDto<List<LlmSessionSummaryResponse>> listSessions(@Valid @RequestBody LlmSessionListReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.listSessions(request.documentId(), accessContext));
  }

  /**
   * 查询指定文档下当前用户的 AI 会话列表。
   *
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return 会话摘要列表。
   */
  @GetMapping("/sessions")
  @Deprecated(forRemoval = false)
  public List<LlmSessionSummaryResponse> listSessions(@RequestParam String documentId, HttpServletRequest request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return llmConversationService.listSessions(documentId, accessContext);
  }

  /**
   * 创建 AI 会话。
   *
   * @param request 创建会话请求体。
   * @param httpRequest 当前 HTTP 请求。
   * @return 新建会话详情。
   */
  @PostMapping("/sessions")
  public ResponseDto<LlmSessionDetailResponse> createSession(@Valid @RequestBody CreateLlmSessionRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.createSession(request, accessContext));
  }

  @PostMapping("/get/session")
  public ResponseDto<LlmSessionDetailResponse> getSession(@Valid @RequestBody LlmSessionGetReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.getSession(request.documentId(), request.sessionId(), accessContext));
  }

  /**
   * 查询单个 AI 会话详情。
   *
   * @param sessionId AI 会话 ID。
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return 会话详情。
   */
  @GetMapping("/sessions/{sessionId}")
  @Deprecated(forRemoval = false)
  public LlmSessionDetailResponse getSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return llmConversationService.getSession(documentId, sessionId, accessContext);
  }

  @PostMapping("/delete/session")
  public ResponseDto<Object> deleteSession(@Valid @RequestBody LlmSessionDeleteReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    llmConversationService.deleteSession(request.documentId(), request.sessionId(), accessContext);
    return successResponse();
  }

  /**
   * 归档删除 AI 会话。
   *
   * @param sessionId AI 会话 ID。
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   */
  @DeleteMapping("/sessions/{sessionId}")
  @Deprecated(forRemoval = false)
  public void deleteSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    llmConversationService.deleteSession(documentId, sessionId, accessContext);
  }

  @PostMapping("/rename/session")
  public ResponseDto<Object> renameSession(@Valid @RequestBody LlmSessionRenameReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    llmConversationService.renameSession(request.documentId(), request.sessionId(), request.title(), accessContext);
    return successResponse();
  }

  /**
   * 重命名 AI 会话。
   *
   * @param sessionId AI 会话 ID。
   * @param documentId 内部文档 ID。
   * @param body 请求体，读取其中的 title 字段。
   * @param request 当前 HTTP 请求。
   */
  @PutMapping("/sessions/{sessionId}/title")
  @Deprecated(forRemoval = false)
  public void renameSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      @RequestBody java.util.Map<String, String> body,
      HttpServletRequest request
  ) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    String newTitle = body.get("title");
    llmConversationService.renameSession(documentId, sessionId, newTitle, accessContext);
  }

  /**
   * 以 SSE 方式发送 AI 消息。
   *
   * @param request 发送消息请求体。
   * @param httpRequest 当前 HTTP 请求。
   * @return SSE emitter。
   */
  @PostMapping(path = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Object streamMessage(@Valid @RequestBody SendLlmMessageRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    try {
      return llmConversationService.streamMessage(request, accessContext);
    } catch (IllegalArgumentException ex) {
      ResponseDto<Object> body = new ResponseDto<>();
      body.setCode("BAD_REQUEST");
      body.setMessage(ex.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body);
    } catch (BaseException ex) {
      ResponseDto<Object> body = new ResponseDto<>();
      body.setCode(ex.getCode());
      body.setMessage(ex.getMessage());
      return ResponseEntity.status(ex.getHttpStatus())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body);
    }
  }

  /**
   * 切换 assistant 消息的当前展示版本。
   *
   * @param messageId assistant 消息 ID。
   * @param request 版本切换请求体。
   * @param httpRequest 当前 HTTP 请求。
   * @return 切换后的 assistant 消息。
   */
  @PutMapping("/messages/{messageId}/active-variant")
  public ResponseDto<LlmMessageResponse> setActiveVariant(
      @PathVariable String messageId,
      @Valid @RequestBody SetLlmActiveVariantRequest request,
      HttpServletRequest httpRequest
  ) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.setActiveVariant(request.documentId(), messageId, request, accessContext));
  }

  /**
   * 兼容旧客户端的同步发送接口。
   *
   * @param request 发送消息请求体。
   * @param httpRequest 当前 HTTP 请求。
   * @return 请求状态快照。
   */
  @Deprecated(forRemoval = false)
  @PostMapping("/messages")
  public ResponseDto<LlmRequestStatusResponse> sendMessage(@Valid @RequestBody SendLlmMessageRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.sendMessage(request, accessContext));
  }

  @PostMapping("/get/request")
  public ResponseDto<LlmRequestStatusResponse> getRequest(@Valid @RequestBody LlmRequestGetReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.getRequest(request.documentId(), request.requestId(), accessContext));
  }

  /**
   * 查询 AI 请求状态。
   *
   * @param requestId AI 请求 ID。
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return 请求状态快照。
   */
  @GetMapping("/requests/{requestId}")
  @Deprecated(forRemoval = false)
  public LlmRequestStatusResponse getRequest(
      @PathVariable String requestId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return llmConversationService.getRequest(documentId, requestId, accessContext);
  }

  @PostMapping("/cancel/request")
  public ResponseDto<LlmRequestStatusResponse> cancelRequest(@Valid @RequestBody LlmRequestCancelReq request) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return successResponseWithData(llmConversationService.cancelRequest(request.documentId(), request.requestId(), accessContext));
  }

  /**
   * 取消正在执行的 AI 请求。
   *
   * @param requestId AI 请求 ID。
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return 取消后的请求状态快照。
   */
  @PostMapping("/requests/{requestId}/cancel")
  @Deprecated(forRemoval = false)
  public LlmRequestStatusResponse cancelRequest(
      @PathVariable String requestId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = CurrentAccessContext.getRequired();
    return llmConversationService.cancelRequest(documentId, requestId, accessContext);
  }
}
