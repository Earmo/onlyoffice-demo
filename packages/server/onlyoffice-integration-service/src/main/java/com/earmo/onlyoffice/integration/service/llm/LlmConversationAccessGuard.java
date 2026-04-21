package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmRequestEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmSessionEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmRequestRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmConversationAccessGuard {

  private final DocumentLlmSessionRepository documentLlmSessionRepository;
  private final DocumentLlmRequestRepository documentLlmRequestRepository;

  public DocumentLlmSessionEntity requireSession(String documentId, String sessionId, AccessContext accessContext) {
    return documentLlmSessionRepository.findSessionByScope(sessionId, documentId, accessContext.tenantId(), accessContext.actorUser())
        .orElseGet(() -> {
          if (documentLlmSessionRepository.findBySessionId(sessionId).isPresent()) {
            throw new LlmApiException(LlmErrorCodes.LLM_SESSION_FORBIDDEN, HttpStatus.FORBIDDEN, "当前用户无权访问该对话会话。");
          }
          throw new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "对话会话不存在。");
        });
  }

  public DocumentLlmRequestEntity requireRequest(String documentId, String requestId, AccessContext accessContext) {
    return documentLlmRequestRepository.findRequestByScope(requestId, documentId, accessContext.tenantId(), accessContext.actorUser())
        .orElseGet(() -> {
          if (documentLlmRequestRepository.findByRequestId(requestId).isPresent()) {
            throw new LlmApiException(LlmErrorCodes.LLM_SESSION_FORBIDDEN, HttpStatus.FORBIDDEN, "当前用户无权访问该对话请求。");
          }
          throw new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "对话请求不存在。");
        });
  }
}
