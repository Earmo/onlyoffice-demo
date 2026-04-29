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
  return postJson("/api/llm/get/capability", { documentId });
}

export function listLlmSessions(documentId) {
  return postJson("/api/llm/list/session", { documentId });
}

export function createLlmSession(documentId, title = "") {
  return postJson("/api/llm/sessions", { documentId, title });
}

export function getLlmSession(sessionId, documentId) {
  return postJson("/api/llm/get/session", { documentId, sessionId });
}

export function deleteLlmSession(sessionId, documentId) {
  return postJson("/api/llm/delete/session", { documentId, sessionId });
}

export function renameLlmSession(sessionId, documentId, title) {
  return postJson("/api/llm/rename/session", { documentId, sessionId, title });
}

export function startLlmMessageStream(payload, handlers) {
  return startLlmMessageStreamTransport(payload, handlers);
}

export function sendLlmMessage(payload) {
  return postJson("/api/llm/messages", payload);
}

export function getLlmRequest(requestId, documentId) {
  return postJson("/api/llm/get/request", { documentId, requestId });
}

export function cancelLlmRequest(requestId, documentId) {
  return postJson("/api/llm/cancel/request", { documentId, requestId });
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
