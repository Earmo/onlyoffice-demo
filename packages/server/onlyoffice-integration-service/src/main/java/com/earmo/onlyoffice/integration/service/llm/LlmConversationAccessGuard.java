package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmRequestEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmSessionEntity;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmMessageRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmRequestRepository;
import com.earmo.onlyoffice.integration.data.repository.DocumentLlmSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AI 会话访问守卫。
 *
 * <p>这一层负责把“资源不存在”和“资源存在但当前用户无权访问”区分开，
 * 避免 service 层反复拼接 repository 查询与异常映射逻辑。
 */
@Component
@RequiredArgsConstructor
public class LlmConversationAccessGuard {

  private final DocumentLlmSessionRepository documentLlmSessionRepository;
  private final DocumentLlmRequestRepository documentLlmRequestRepository;
  private final DocumentLlmMessageRepository documentLlmMessageRepository;

  /**
   * 校验并返回当前用户有权访问的会话。
   *
   * <p>处理步骤：
   * 1. 先按文档、租户、用户作用域查询会话；
   * 2. 若作用域内不存在，再按主键检查资源是否真实存在；
   * 3. 存在但越权返回 `403`，否则返回 `404`。
   */
  public DocumentLlmSessionEntity requireSession(String documentId, String sessionId, AccessContext accessContext) {
    return documentLlmSessionRepository.findSessionByScope(sessionId, documentId, accessContext.tenantId(), accessContext.actorUser())
        .orElseGet(() -> {
          if (documentLlmSessionRepository.findBySessionId(sessionId).isPresent()) {
            throw new LlmApiException(LlmErrorCodes.LLM_SESSION_FORBIDDEN, HttpStatus.FORBIDDEN, "当前用户无权访问该对话会话。");
          }
          throw new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "对话会话不存在。");
        });
  }

  /**
   * 校验并返回当前用户有权访问的请求。
   *
   * <p>和 {@link #requireSession(String, String, AccessContext)} 保持相同语义：
   * 先查作用域，再区分越权和不存在。
   */
  public DocumentLlmRequestEntity requireRequest(String documentId, String requestId, AccessContext accessContext) {
    return documentLlmRequestRepository.findRequestByScope(requestId, documentId, accessContext.tenantId(), accessContext.actorUser())
        .orElseGet(() -> {
          if (documentLlmRequestRepository.findByRequestId(requestId).isPresent()) {
            throw new LlmApiException(LlmErrorCodes.LLM_SESSION_FORBIDDEN, HttpStatus.FORBIDDEN, "当前用户无权访问该对话请求。");
          }
          throw new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "对话请求不存在。");
        });
  }

  public DocumentLlmMessageEntity requireAssistantMessage(
      String documentId,
      String sessionId,
      String messageId,
      AccessContext accessContext
  ) {
    DocumentLlmMessageEntity message = documentLlmMessageRepository.findMessageByScope(
            messageId,
            documentId,
            accessContext.tenantId(),
            accessContext.actorUser()
        )
        .orElseThrow(() -> new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, "assistant 消息不存在。"));
    if (!sessionId.equals(message.getSessionId())) {
      throw new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.BAD_REQUEST, "regenerate assistant message 不属于当前会话。");
    }
    if (!"assistant".equals(message.getRole())) {
      throw new LlmApiException(LlmErrorCodes.LLM_SESSION_NOT_FOUND, HttpStatus.BAD_REQUEST, "regenerate 目标必须是 assistant 消息。");
    }
    return message;
  }
}
