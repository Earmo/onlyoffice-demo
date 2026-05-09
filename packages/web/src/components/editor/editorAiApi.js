import { apiFetch, parseJsonEnvelope } from "../../lib/api";
import { startLlmMessageStream as startLlmMessageStreamTransport } from "./llmMessageStream.js";

function postJson(path, body) {
  return apiFetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  }).then(parseJsonEnvelope);
}

export function getLlmCapability(documentId) {
  return postJson("/api/llm/capability/query", { documentId });
}

export function listLlmSessions(documentId) {
  return postJson("/api/llm/sessions/list", { documentId });
}

export function createLlmSession(documentId, title = "") {
  return postJson("/api/llm/sessions/create", { documentId, title });
}

export function getLlmSession(sessionId, documentId) {
  return postJson("/api/llm/sessions/detail", { documentId, sessionId });
}

export function deleteLlmSession(sessionId, documentId) {
  return postJson("/api/llm/sessions/delete", { documentId, sessionId });
}

export function renameLlmSession(sessionId, documentId, title) {
  return postJson("/api/llm/sessions/rename", { documentId, sessionId, title });
}

export function startLlmMessageStream(payload, handlers) {
  return startLlmMessageStreamTransport(payload, handlers);
}

export function getLlmRequest(requestId, documentId) {
  return postJson("/api/llm/requests/detail", { documentId, requestId });
}

export function cancelLlmRequest(requestId, documentId) {
  return postJson("/api/llm/requests/cancel", { documentId, requestId });
}

export function setLlmActiveVariant({ documentId, sessionId, assistantMessageId, variantId = "", variantIndex }) {
  return apiFetch(`/api/llm/messages/${encodeURIComponent(assistantMessageId)}/active-variant`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      documentId,
      sessionId,
      variantId,
      variantIndex
    })
  }).then(parseJsonEnvelope);
}
