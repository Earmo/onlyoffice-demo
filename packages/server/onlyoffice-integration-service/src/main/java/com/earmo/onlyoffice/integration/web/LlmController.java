package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.model.llm.CreateLlmSessionRequest;
import com.earmo.onlyoffice.integration.model.llm.LlmCapabilityResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmRequestStatusResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionDetailResponse;
import com.earmo.onlyoffice.integration.model.llm.LlmSessionSummaryResponse;
import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.earmo.onlyoffice.integration.service.llm.LlmConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

  private final LlmConversationService llmConversationService;
  private final AccessContextResolver accessContextResolver;

  @GetMapping("/capability")
  public LlmCapabilityResponse capability(@RequestParam String documentId, HttpServletRequest request) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.getCapability(documentId, accessContext);
  }

  @GetMapping("/sessions")
  public List<LlmSessionSummaryResponse> listSessions(@RequestParam String documentId, HttpServletRequest request) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.listSessions(documentId, accessContext);
  }

  @PostMapping("/sessions")
  public LlmSessionDetailResponse createSession(@Valid @RequestBody CreateLlmSessionRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = accessContextResolver.resolve(httpRequest);
    return llmConversationService.createSession(request, accessContext);
  }

  @GetMapping("/sessions/{sessionId}")
  public LlmSessionDetailResponse getSession(
      @PathVariable String sessionId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.getSession(documentId, sessionId, accessContext);
  }

  @PostMapping("/messages")
  public LlmRequestStatusResponse sendMessage(@Valid @RequestBody SendLlmMessageRequest request, HttpServletRequest httpRequest) {
    AccessContext accessContext = accessContextResolver.resolve(httpRequest);
    return llmConversationService.sendMessage(request, accessContext);
  }

  @GetMapping("/requests/{requestId}")
  public LlmRequestStatusResponse getRequest(
      @PathVariable String requestId,
      @RequestParam String documentId,
      HttpServletRequest request
  ) {
    AccessContext accessContext = accessContextResolver.resolve(request);
    return llmConversationService.getRequest(documentId, requestId, accessContext);
  }

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
