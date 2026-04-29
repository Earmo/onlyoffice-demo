package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.llm.CreateLlmSessionRequest;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmMessageResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDetailResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionSummaryResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.earmo.onlyoffice.integration.model.llm.SetLlmActiveVariantRequest;
import com.earmo.onlyoffice.integration.service.llm.LlmConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

  private final LlmConversationService llmConversationService;
  private final AccessContextResolver accessContextResolver;

  /**
   * 查询指定文档的 AI 能力。
   *
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return AI 能力响应。
   */
  @GetMapping("/capability")
  public LlmCapabilityResponse capability(@RequestParam String documentId, HttpServletRequest request) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.getCapability(documentId, accessContext);
  }

  /**
   * 查询指定文档下当前用户的 AI 会话列表。
   *
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   * @return 会话摘要列表。
   */
  @GetMapping("/sessions")
  public List<LlmSessionSummaryResponse> listSessions(@RequestParam String documentId, HttpServletRequest request) {
    AccessContext accessContext = accessContextResolver.resolve(request);
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
  public LlmSessionDetailResponse createSession(@Valid @RequestBody CreateLlmSessionRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = accessContextResolver.resolve(httpRequest);
    return llmConversationService.createSession(request, accessContext);
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
  public LlmSessionDetailResponse getSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.getSession(documentId, sessionId, accessContext);
  }

  /**
   * 归档删除 AI 会话。
   *
   * @param sessionId AI 会话 ID。
   * @param documentId 内部文档 ID。
   * @param request 当前 HTTP 请求。
   */
  @DeleteMapping("/sessions/{sessionId}")
  public void deleteSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    llmConversationService.deleteSession(documentId, sessionId, accessContext);
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
  public void renameSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      @RequestBody java.util.Map<String, String> body,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
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
  public SseEmitter streamMessage(@Valid @RequestBody SendLlmMessageRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = accessContextResolver.resolve(httpRequest);
    return llmConversationService.streamMessage(request, accessContext);
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
  public LlmMessageResponse setActiveVariant(
      @PathVariable String messageId,
      @Valid @RequestBody SetLlmActiveVariantRequest request,
      HttpServletRequest httpRequest
  ) {
    AccessContext accessContext = accessContextResolver.resolve(httpRequest);
    return llmConversationService.setActiveVariant(request.documentId(), messageId, request, accessContext);
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
  public LlmRequestStatusResponse sendMessage(@Valid @RequestBody SendLlmMessageRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = accessContextResolver.resolve(httpRequest);
    return llmConversationService.sendMessage(request, accessContext);
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
  public LlmRequestStatusResponse getRequest(
      @PathVariable String requestId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.getRequest(documentId, requestId, accessContext);
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
  public LlmRequestStatusResponse cancelRequest(
      @PathVariable String requestId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.cancelRequest(documentId, requestId, accessContext);
  }
}
