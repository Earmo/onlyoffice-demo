import { apiFetch } from "../../lib/api";
import { startLlmMessageStream as startLlmMessageStreamTransport } from "./llmMessageStream.js";

async function parseApiResponse(response) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload?.message || `请求失败，HTTP ${response.status}`);
    error.status = response.status;
    error.errorCode = payload?.errorCode || "";
    error.payload = payload;
    throw error;
  }
  return payload;
}

export function getLlmCapability(documentId) {
  return apiFetch(`/api/llm/capability?documentId=${encodeURIComponent(documentId)}`).then(parseApiResponse);
}

export function listLlmSessions(documentId) {
  return apiFetch(`/api/llm/sessions?documentId=${encodeURIComponent(documentId)}`).then(parseApiResponse);
}

export function createLlmSession(documentId, title = "") {
  return apiFetch("/api/llm/sessions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ documentId, title })
  }).then(parseApiResponse);
}

export function getLlmSession(sessionId, documentId) {
  return apiFetch(`/api/llm/sessions/${encodeURIComponent(sessionId)}?documentId=${encodeURIComponent(documentId)}`).then(parseApiResponse);
}

export function startLlmMessageStream(payload, handlers) {
  return startLlmMessageStreamTransport(payload, handlers);
}

export function sendLlmMessage(payload) {
  return apiFetch("/api/llm/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  }).then(parseApiResponse);
}

export function getLlmRequest(requestId, documentId) {
  return apiFetch(`/api/llm/requests/${encodeURIComponent(requestId)}?documentId=${encodeURIComponent(documentId)}`).then(parseApiResponse);
}

export function cancelLlmRequest(requestId, documentId) {
  return apiFetch(`/api/llm/requests/${encodeURIComponent(requestId)}/cancel?documentId=${encodeURIComponent(documentId)}`, {
    method: "POST"
  }).then(parseApiResponse);
}
