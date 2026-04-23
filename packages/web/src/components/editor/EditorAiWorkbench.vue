<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { Plus, Menu, Crop, List, Picture, Close, Position, Collection, DocumentCopy, Refresh, Delete } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  cancelLlmRequest,
  createLlmSession,
  getLlmCapability,
  getLlmRequest,
  getLlmSession,
  listLlmSessions,
  startLlmMessageStream
} from "./editorAiApi";
import markdownit from "markdown-it";
import hljs from "highlight.js";
import "highlight.js/styles/github.css";

const md = markdownit({
  html: true,
  linkify: true,
  typographer: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang }).value;
      } catch {
        return "";
      }
    }
    return "";
  }
});

const props = defineProps({
  documentTitle: {
    type: String,
    default: ""
  },
  runtimeContext: {
    type: Object,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  closing: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["capture-selection", "refresh-outline", "jump-to-heading", "insert-image"]);

const drawerVisible = ref(false);
const capabilityStatus = ref("bridge-pending");
const capability = ref(null);
const disabledReason = ref("");
const sessions = ref([]);
const currentSessionId = ref("");
const currentSessionTitle = ref("新会话");
const currentSessionContextSignature = ref("");
const conversationEntries = ref([]);
const currentRequestId = ref("");
const currentRequestState = ref("");
const lastCancelled = ref(false);
const draftQuestion = ref("");
const threadError = ref(null);
const retryEntry = ref(null);
const showRetryDialog = ref(false);
const showSnapshotDecision = ref(false);
const isExcludedSelection = ref(false);
const selectedProvider = ref("");
const selectedModel = ref("");
const activeStream = ref(null);

let bootstrapToken = 0;
let sessionLoadToken = 0;
let sessionLoadRequestedId = "";

const providerOptions = computed(() => capability.value?.availableProviders || []);
const availableModels = computed(() => {
  return providerOptions.value.find(option => option.provider === selectedProvider.value)?.availableModels || [];
});
const snapshotState = computed(() => {
  if (!props.runtimeContext.bridgeReady) {
    return "bridge-pending";
  }
  return props.runtimeContext.hasEmptySelection || !props.runtimeContext.selectedText ? "snapshot-empty" : "snapshot-ready";
});
const liveContextSignature = computed(() => {
  const selectionText = isExcludedSelection.value ? "" : props.runtimeContext.selectedText;
  const isEmpty = isExcludedSelection.value ? true : props.runtimeContext.hasEmptySelection;
  return buildContextSignature({
    text: selectionText,
    emptySelection: isEmpty,
    headingText: props.runtimeContext.activeHeadingNode?.text || props.runtimeContext.activeHeadingNode?.label || ""
  });
});
const currentHeadingText = computed(() => props.runtimeContext.activeHeadingNode?.text || props.runtimeContext.activeHeadingNode?.label || "");
const topStatusText = computed(() => {
  if (capabilityStatus.value === "capability-disabled") return "模型暂不可用";
  if (snapshotState.value === "bridge-pending") return "正在准备上下文";
  if (currentRequestState.value === "cancelling") return "正在取消请求";
  if (currentRequestState.value === "in_progress") return "正在接收流式响应";
  if (lastCancelled.value) return "请求已取消";
  return props.runtimeContext.bridgeStatusMessage || "等待上下文准备完成。";
});
const canSend = computed(() => {
  return !props.loading
    && !props.closing
    && capabilityStatus.value === "capability-enabled"
    && !currentRequestId.value
    && Boolean(draftQuestion.value.trim());
});
const flatOutlineItems = computed(() => {
  const result = [];
  function flatten(nodes, depth = 0) {
    for (const node of nodes || []) {
      result.push({ ...node, depth });
      if (node.children?.length) {
        flatten(node.children, depth + 1);
      }
    }
  }
  flatten(props.runtimeContext.outlineTreeData || []);
  return result;
});

watch(
  () => props.runtimeContext.selectedText,
  () => {
    isExcludedSelection.value = false;
  }
);

watch(
  () => props.runtimeContext.documentId,
  async documentId => {
    resetWorkbench();
    if (!documentId) {
      return;
    }
    await loadCapabilityAndBootstrap(documentId);
  },
  { immediate: true }
);

watch(providerOptions, options => {
  if (!options.length) {
    selectedProvider.value = "";
    selectedModel.value = "";
    return;
  }
  if (!options.some(option => option.provider === selectedProvider.value)) {
    selectedProvider.value = capability.value?.defaultProvider || options[0].provider;
  }
  const models = options.find(option => option.provider === selectedProvider.value)?.availableModels || [];
  if (!models.includes(selectedModel.value)) {
    selectedModel.value = capability.value?.defaultModel || models[0] || "";
  }
});

watch(selectedProvider, provider => {
  const option = providerOptions.value.find(item => item.provider === provider);
  const models = option?.availableModels || [];
  if (!models.includes(selectedModel.value)) {
    selectedModel.value = option?.defaultModel || models[0] || "";
  }
});

onBeforeUnmount(() => {
  abortActiveStream();
});

function resetWorkbench() {
  abortActiveStream();
  capabilityStatus.value = props.runtimeContext.bridgeReady ? "capability-loading" : "bridge-pending";
  capability.value = null;
  disabledReason.value = "";
  sessions.value = [];
  currentSessionId.value = "";
  currentSessionTitle.value = "新会话";
  currentSessionContextSignature.value = "";
  conversationEntries.value = [];
  currentRequestId.value = "";
  currentRequestState.value = "";
  lastCancelled.value = false;
  draftQuestion.value = "";
  threadError.value = null;
  retryEntry.value = null;
  showRetryDialog.value = false;
  showSnapshotDecision.value = false;
  selectedProvider.value = "";
  selectedModel.value = "";
}

function abortActiveStream() {
  activeStream.value?.abort?.();
  activeStream.value = null;
  currentRequestId.value = "";
  currentRequestState.value = "";
}

function hasActiveRequestState() {
  return Boolean(activeStream.value || currentRequestId.value || currentRequestState.value === "in_progress");
}

async function confirmSessionSwitch(targetSessionId) {
  if (!shouldConfirmSessionSwitch(targetSessionId)) {
    return true;
  }
  try {
    await ElMessageBox.confirm(
      "当前回答仍在生成中，切换会话会停止本次回复。是否继续切换？",
      "切换会话",
      {
        confirmButtonText: "继续切换",
        cancelButtonText: "留在当前会话",
        type: "warning"
      }
    );
    ElMessage.info("已停止当前回复，正在切换会话");
    return true;
  } catch {
    return false;
  }
}

function shouldConfirmSessionSwitch(targetSessionId) {
  if (!targetSessionId || targetSessionId === currentSessionId.value) {
    return false;
  }
  return hasActiveRequestState();
}

async function loadCapabilityAndBootstrap(documentId) {
  // 先拿 capability，再决定是否自动恢复/创建会话。
  // bootstrapToken 用来丢弃切文档过程中的晚到响应，避免旧文档状态污染当前工作台。
  const token = ++bootstrapToken;
  capabilityStatus.value = props.runtimeContext.bridgeReady ? "capability-loading" : "bridge-pending";
  threadError.value = null;
  try {
    const capabilityPayload = await getLlmCapability(documentId);
    if (isBootstrapStale(token, documentId)) {
      return;
    }
    capability.value = capabilityPayload;
    selectedProvider.value = capabilityPayload.defaultProvider || capabilityPayload.provider || "";
    selectedModel.value = capabilityPayload.defaultModel || capabilityPayload.model || "";
    if (!capabilityPayload.llmAvailable) {
      capabilityStatus.value = "capability-disabled";
      disabledReason.value = capabilityPayload.disabledReason || "LLM_DISABLED";
      return;
    }
    capabilityStatus.value = "capability-enabled";
    await refreshSessions(documentId, token);
    await bootstrapSession(documentId, token);
  } catch (error) {
    if (isBootstrapStale(token, documentId)) {
      return;
    }
    capabilityStatus.value = "capability-disabled";
    disabledReason.value = error.errorCode || error.message || "LLM_UNAVAILABLE";
  }
}

async function refreshSessions(documentId, token = bootstrapToken) {
  try {
    const sessionList = await listLlmSessions(documentId);
    if (isBootstrapStale(token, documentId)) {
      return;
    }
    sessions.value = Array.isArray(sessionList) ? sessionList : [];
  } catch (error) {
    if (!isBootstrapStale(token, documentId)) {
      threadError.value = toThreadError(error);
    }
  }
}

async function bootstrapSession(documentId, token) {
  // Phase 14.2 默认优先复用最近会话；没有历史会话时才创建新的空线程。
  const sessionList = await listLlmSessions(documentId);
  if (isBootstrapStale(token, documentId)) {
    return;
  }
  if (Array.isArray(sessionList) && sessionList.length > 0) {
    sessions.value = sessionList;
    await selectSession(sessionList[0].sessionId);
    return;
  }
  const session = await createLlmSession(documentId);
  if (isBootstrapStale(token, documentId) || session.documentId !== props.runtimeContext.documentId) {
    return;
  }
  applySessionSummary(session);
  await refreshSessions(documentId, token);
}

async function selectSession(sessionId) {
  return selectSessionWithOptions(sessionId, { confirmAbort: false });
}

async function selectSessionWithOptions(sessionId, options = {}) {
  const documentId = props.runtimeContext.documentId;
  if (!sessionId || !documentId) {
    return false;
  }
  if (options.confirmAbort && !await confirmSessionSwitch(sessionId)) {
    return false;
  }
  if (hasActiveRequestState()) {
    const cancelled = await cancelActiveRequest();
    if (!cancelled) {
      return false;
    }
  }
  const token = ++sessionLoadToken;
  sessionLoadRequestedId = sessionId;
  threadError.value = null;
  try {
    const session = await getLlmSession(sessionId, documentId);
    if (isSessionLoadStale(token, sessionId, documentId)) {
      return false;
    }
    applySessionSummary(session);
    conversationEntries.value = buildConversationEntries(session.messages || []);
    return true;
  } catch (error) {
    if (isSessionLoadStale(token, sessionId, documentId)) {
      return false;
    }
    threadError.value = toThreadError(error);
    if (error.errorCode === "LLM_SESSION_NOT_FOUND" || error.errorCode === "LLM_SESSION_FORBIDDEN") {
      currentSessionId.value = "";
      conversationEntries.value = [];
      try {
        const fallbackSession = await createLlmSession(documentId);
        applySessionSummary(fallbackSession);
        await refreshSessions(documentId);
        return true;
      } catch (fallbackError) {
        threadError.value = toThreadError(fallbackError);
      }
    }
    return false;
  }
}

async function handleSessionClick(sessionId) {
  const switched = await selectSessionWithOptions(sessionId, { confirmAbort: true });
  if (switched) {
    drawerVisible.value = false;
  }
}

function applySessionSummary(session) {
  currentSessionId.value = session.sessionId;
  currentSessionTitle.value = session.title || "新会话";
  currentSessionContextSignature.value = buildContextSignature({
    text: session.lastSnapshotText,
    emptySelection: session.lastSnapshotIsEmpty,
    headingText: session.lastHeadingText
  });
}

function buildConversationEntries(messages) {
  const entries = [];
  for (const message of messages) {
    if (message.role === "user") {
      entries.push({
        key: message.messageId,
        question: message.question || "",
        selectionSnapshot: {
          text: message.snapshotText || "",
          emptySelection: Boolean(message.snapshotEmptySelection)
        },
        headingContext: {
          includeHeading: Boolean(message.includeHeading),
          headingId: message.headingId || "",
          headingText: message.headingText || ""
        },
        assistantMessageId: "",
        requestId: "",
        status: "completed",
        assistantText: "",
        streamingText: "",
        usage: null,
        finishReason: "",
        providerResponseMeta: {},
        errorCode: "",
        responseMessage: ""
      });
      continue;
    }
    const lastEntry = entries.at(-1);
    if (lastEntry && !lastEntry.assistantMessageId) {
      lastEntry.assistantMessageId = message.messageId;
      lastEntry.status = message.status || "completed";
      lastEntry.assistantText = message.assistantText || "";
      lastEntry.streamingText = "";
      lastEntry.usage = message.usage || null;
      lastEntry.finishReason = message.finishReason || "";
      lastEntry.providerResponseMeta = message.providerResponseMeta || {};
      lastEntry.errorCode = message.errorCode || "";
      lastEntry.responseMessage = humanizeResponseState(message);
      continue;
    }
    entries.push({
      key: message.messageId,
      question: "",
      selectionSnapshot: {
        text: message.snapshotText || "",
        emptySelection: Boolean(message.snapshotEmptySelection)
      },
      headingContext: {
        includeHeading: Boolean(message.includeHeading),
        headingId: message.headingId || "",
        headingText: message.headingText || ""
      },
      assistantMessageId: message.messageId,
      requestId: "",
      status: message.status || "completed",
      assistantText: message.assistantText || "",
      streamingText: "",
      usage: message.usage || null,
      finishReason: message.finishReason || "",
      providerResponseMeta: message.providerResponseMeta || {},
      errorCode: message.errorCode || "",
      responseMessage: humanizeResponseState(message)
    });
  }
  return entries;
}

async function handleSendClick() {
  if (!canSend.value) {
    return;
  }
  if (conversationEntries.value.length > 0 && currentSessionContextSignature.value && currentSessionContextSignature.value !== liveContextSignature.value) {
    showSnapshotDecision.value = true;
    return;
  }
  await sendCurrentQuestion({ retryConfirmed: false, createNewSessionFirst: false });
}

async function sendCurrentQuestion(options) {
  const mode = options || {};
  const retryPayload = mode.retryPayload || null;
  const documentId = props.runtimeContext.documentId;
  const question = retryPayload ? retryPayload.question : draftQuestion.value.trim();
  if (!question || !documentId) {
    return;
  }

  abortActiveStream();
  lastCancelled.value = false;
  threadError.value = null;

  let targetSessionId = currentSessionId.value;
  try {
    if (mode.createNewSessionFirst || !targetSessionId) {
      const session = await createLlmSession(documentId);
      if (session.documentId !== props.runtimeContext.documentId) {
        return;
      }
      applySessionSummary(session);
      targetSessionId = session.sessionId;
      await refreshSessions(documentId);
    }

    const payload = retryPayload ? {
      documentId,
      sessionId: targetSessionId,
      provider: selectedProvider.value,
      model: selectedModel.value,
      question: retryPayload.question,
      selectionSnapshot: retryPayload.selectionSnapshot,
      headingContext: retryPayload.headingContext,
      retryConfirmed: true
    } : {
      documentId,
      sessionId: targetSessionId,
      provider: selectedProvider.value,
      model: selectedModel.value,
      question,
      selectionSnapshot: {
        text: isExcludedSelection.value ? "" : props.runtimeContext.selectedText || "",
        emptySelection: isExcludedSelection.value || Boolean(props.runtimeContext.hasEmptySelection || !props.runtimeContext.selectedText)
      },
      headingContext: {
        includeHeading: Boolean(currentHeadingText.value),
        headingId: props.runtimeContext.activeHeadingId || "",
        headingText: currentHeadingText.value
      },
      retryConfirmed: Boolean(mode.retryConfirmed)
    };

    const pendingEntry = {
      key: `pending-${Date.now()}`,
      question: payload.question,
      selectionSnapshot: payload.selectionSnapshot,
      headingContext: payload.headingContext,
      assistantMessageId: "",
      requestId: "",
      status: "in_progress",
      assistantText: "",
      streamingText: "",
      usage: null,
      finishReason: "",
      providerResponseMeta: {
        provider: payload.provider,
        model: payload.model
      },
      errorCode: "",
      responseMessage: "等待模型返回..."
    };
    // UI 先插入一个本地 pending 条目，后面再用 request-started 补齐 requestId/assistantMessageId。
    conversationEntries.value = [...conversationEntries.value, pendingEntry];
    currentSessionContextSignature.value = buildContextSignature({
      text: payload.selectionSnapshot.text,
      emptySelection: payload.selectionSnapshot.emptySelection,
      headingText: payload.headingContext.headingText
    });

    const stream = startLlmMessageStream(payload, {
      onStarted(event) {
        if (!isActiveStreamTarget(documentId, targetSessionId)) {
          return;
        }
        pendingEntry.assistantMessageId = event.assistantMessageId || pendingEntry.assistantMessageId;
        pendingEntry.requestId = event.requestId || pendingEntry.requestId;
        pendingEntry.providerResponseMeta = {
          ...(pendingEntry.providerResponseMeta || {}),
          ...(event.providerResponseMeta || {}),
          provider: event.provider || pendingEntry.providerResponseMeta?.provider,
          model: event.model || pendingEntry.providerResponseMeta?.model
        };
        currentRequestId.value = event.requestId || "";
        currentRequestState.value = "in_progress";
        conversationEntries.value = [...conversationEntries.value];
      },
      onDelta(event) {
        if (!isCurrentRequestTarget(documentId, targetSessionId, pendingEntry.requestId || event?.requestId || "")) {
          return;
        }
        pendingEntry.status = "in_progress";
        pendingEntry.streamingText = `${pendingEntry.streamingText || ""}${event?.delta || ""}`;
        pendingEntry.responseMessage = "正在请求模型";
        conversationEntries.value = [...conversationEntries.value];
      },
      onMeta(event) {
        if (!isCurrentRequestTarget(documentId, targetSessionId, pendingEntry.requestId || event?.requestId || "")) {
          return;
        }
        pendingEntry.usage = event?.usage || pendingEntry.usage;
        pendingEntry.finishReason = event?.finishReason || pendingEntry.finishReason;
        pendingEntry.providerResponseMeta = {
          ...(pendingEntry.providerResponseMeta || {}),
          ...(event?.providerResponseMeta || {})
        };
        conversationEntries.value = [...conversationEntries.value];
      },
      onCompleted(event) {
        if (!isCurrentRequestTarget(documentId, targetSessionId, pendingEntry.requestId || event?.requestId || "")) {
          return;
        }
        applyStreamTerminalResult(pendingEntry, {
          ...event,
          status: "completed",
          assistantText: event?.assistantText || pendingEntry.streamingText
        });
      },
      onCancelled(event) {
        if (!isCurrentRequestTarget(documentId, targetSessionId, pendingEntry.requestId || event?.requestId || "")) {
          return;
        }
        applyStreamTerminalResult(pendingEntry, {
          ...event,
          status: "cancelled",
          errorCode: event?.errorCode || "LLM_REQUEST_CANCELLED"
        });
        lastCancelled.value = true;
      },
      async onError(eventOrError) {
        if (isTerminalStreamPayload(eventOrError)) {
          applyStreamTerminalResult(pendingEntry, {
            ...eventOrError,
            status: "failed"
          });
          return;
        }
        // 网络中断或页面切换导致的非终态异常，只允许做一次 request 查询补偿。
        await reconcileRequestOnce(pendingEntry, targetSessionId, documentId, eventOrError);
      },
      async onComplete() {
        if (pendingEntry.status === "in_progress") {
          // 某些代理层会直接结束连接而不补 terminal event，这里也做一次最终态对账。
          await reconcileRequestOnce(pendingEntry, targetSessionId, documentId, null);
        }
      }
    });

    activeStream.value = stream;
    currentRequestState.value = "in_progress";
    await stream.ready;
    if (!retryPayload) {
      draftQuestion.value = "";
    }
  } catch (error) {
    markPendingEntryFailed(conversationEntries.value.at(-1), error);
  }
}

async function reconcileRequestOnce(entry, sessionId, documentId, originalError) {
  // 流式通道异常时，只做一次最终态回查，避免浏览器和服务端反复轮询同一 request。
  if (!entry || entry.reconciled) {
    return;
  }
  entry.reconciled = true;
  if (!entry.requestId) {
    markPendingEntryFailed(entry, originalError || apiLikeError("STREAM_INTERRUPTED", "流式请求中断"));
    return;
  }
  try {
    const result = await getLlmRequest(entry.requestId, documentId);
    if (!isCurrentRequestTarget(documentId, sessionId, entry.requestId)) {
      return;
    }
    if (["completed", "failed", "cancelled"].includes(result.status)) {
      applyRequestResult(entry, result);
      return;
    }
    markPendingEntryFailed(entry, originalError || apiLikeError("STREAM_INTERRUPTED", "流式请求中断"));
  } catch (error) {
    markPendingEntryFailed(entry, error);
  }
}

async function cancelSending() {
  if (!hasActiveRequestState()) {
    return;
  }
  await cancelActiveRequest();
}

function applyStreamTerminalResult(entry, event) {
  // 把 SSE terminal event 规范化成与 GET /requests/{id} 相同的结构，后续统一复用 applyRequestResult。
  applyRequestResult(entry, {
    documentId: props.runtimeContext.documentId,
    requestId: event.requestId || entry.requestId,
    sessionId: event.sessionId || currentSessionId.value,
    assistantMessageId: event.assistantMessageId || entry.assistantMessageId,
    status: event.status,
    assistantText: event.assistantText || "",
    usage: event.usage || null,
    finishReason: event.finishReason || "",
    providerResponseMeta: event.providerResponseMeta || entry.providerResponseMeta || {},
    errorCode: event.errorCode || "",
    startedTime: event.startedTime || "",
    finishedTime: event.finishedTime || ""
  });
}

function applyRequestResult(entry, result) {
  if (!entry) {
    return;
  }
  // 不论结果来自流式终态、取消接口还是最终态回查，UI 收口都在这里，避免三套状态机。
  entry.assistantMessageId = result.assistantMessageId || entry.assistantMessageId;
  entry.requestId = result.requestId || entry.requestId;
  entry.status = result.status;
  entry.assistantText = result.assistantText || "";
  entry.streamingText = "";
  entry.usage = result.usage || null;
  entry.finishReason = result.finishReason || "";
  entry.providerResponseMeta = result.providerResponseMeta || entry.providerResponseMeta || {};
  entry.errorCode = result.errorCode || "";
  entry.responseMessage = humanizeResult(result);
  conversationEntries.value = [...conversationEntries.value];
  if (["completed", "failed", "cancelled"].includes(result.status) && currentRequestId.value === entry.requestId) {
    currentRequestId.value = "";
    currentRequestState.value = "";
    activeStream.value = null;
  }
}

async function cancelActiveRequest() {
  const currentEntry = findActiveConversationEntry();
  if (!currentEntry) {
    abortActiveStream();
    return true;
  }

  currentRequestState.value = "cancelling";
  try {
    if (currentRequestId.value) {
      const result = await cancelLlmRequest(currentRequestId.value, props.runtimeContext.documentId);
      activeStream.value?.abort?.();
      const resultEntry = conversationEntries.value.find(entry => entry.requestId === result.requestId) || currentEntry;
      applyRequestResult(resultEntry, result);
    } else {
      activeStream.value?.abort?.();
      applyRequestResult(currentEntry, buildLocalCancelledResult(currentEntry));
    }
    lastCancelled.value = true;
    return true;
  } catch (error) {
    currentRequestState.value = "in_progress";
    threadError.value = toThreadError(error);
    return false;
  }
}

function findActiveConversationEntry() {
  if (currentRequestId.value) {
    return conversationEntries.value.find(entry => entry.requestId === currentRequestId.value) || conversationEntries.value.at(-1) || null;
  }
  return [...conversationEntries.value].reverse().find(entry => entry.status === "in_progress") || null;
}

function buildLocalCancelledResult(entry) {
  return {
    documentId: props.runtimeContext.documentId,
    requestId: entry.requestId || "",
    sessionId: currentSessionId.value,
    assistantMessageId: entry.assistantMessageId || "",
    status: "cancelled",
    assistantText: "",
    usage: null,
    finishReason: "",
    providerResponseMeta: entry.providerResponseMeta || {},
    errorCode: "LLM_REQUEST_CANCELLED",
    startedTime: "",
    finishedTime: ""
  };
}

function markPendingEntryFailed(entry, error) {
  if (!entry) {
    return;
  }
  entry.status = "failed";
  entry.errorCode = error?.errorCode || "NETWORK_ERROR";
  entry.responseMessage = error?.message || "请求失败";
  entry.streamingText = "";
  conversationEntries.value = [...conversationEntries.value];
  currentRequestId.value = "";
  currentRequestState.value = "";
  activeStream.value = null;
  threadError.value = toThreadError(error);
}

function isTerminalStreamPayload(value) {
  return Boolean(value && typeof value === "object" && ("errorCode" in value || "requestId" in value));
}

function isActiveStreamTarget(documentId, sessionId) {
  return documentId === props.runtimeContext.documentId && sessionId === currentSessionId.value;
}

function isCurrentRequestTarget(documentId, sessionId, requestId) {
  return documentId === props.runtimeContext.documentId
    && sessionId === currentSessionId.value
    && (!currentRequestId.value || requestId === currentRequestId.value || !requestId);
}

function humanizeResult(result) {
  if (result.status === "failed") {
    return result.errorCode || "请求失败";
  }
  if (result.status === "cancelled") {
    return "已取消";
  }
  if (result.status === "completed") {
    return "完成";
  }
  return "等待模型返回...";
}

function humanizeResponseState(message) {
  if (message.status === "failed") {
    return message.errorCode || "请求失败";
  }
  if (message.status === "cancelled") {
    return "已取消";
  }
  return message.status || "completed";
}

function handleRemoveSelection() {
  isExcludedSelection.value = true;
}

function handleManualCapture() {
  isExcludedSelection.value = false;
  emit("capture-selection");
}

function buildContextSignature(context) {
  return JSON.stringify([
    context?.text || "",
    Boolean(context?.emptySelection),
    context?.headingText || ""
  ]);
}

function isBootstrapStale(token, documentId) {
  // 切文档后的 capability/session 晚到响应必须直接丢弃。
  return token !== bootstrapToken || documentId !== props.runtimeContext.documentId;
}

function isSessionLoadStale(token, sessionId, documentId) {
  // 切线程后的详情请求也需要同样的 stale guard，避免旧线程消息覆盖当前线程。
  return token !== sessionLoadToken || sessionId !== sessionLoadRequestedId || documentId !== props.runtimeContext.documentId;
}

function toThreadError(error) {
  return {
    errorCode: error?.errorCode || "",
    message: error?.message || "请求失败"
  };
}

function apiLikeError(errorCode, message) {
  const error = new Error(message);
  error.errorCode = errorCode;
  return error;
}

function openRetryDialog(entry) {
  retryEntry.value = entry;
  showRetryDialog.value = true;
}

async function confirmRetry() {
  if (!retryEntry.value) {
    return;
  }
  showRetryDialog.value = false;
  await sendCurrentQuestion({
    retryConfirmed: true,
    retryPayload: {
      question: retryEntry.value.question,
      selectionSnapshot: retryEntry.value.selectionSnapshot,
      headingContext: retryEntry.value.headingContext
    }
  });
}

function confirmSnapshotDecision(createNewSessionFirst) {
  showSnapshotDecision.value = false;
  void sendCurrentQuestion({ retryConfirmed: false, createNewSessionFirst });
}

async function startNewChat() {
  abortActiveStream();
  currentSessionId.value = "";
  currentSessionTitle.value = "新会话";
  currentSessionContextSignature.value = "";
  conversationEntries.value = [];
  draftQuestion.value = "";
  if (props.runtimeContext.documentId) {
    try {
      const session = await createLlmSession(props.runtimeContext.documentId);
      applySessionSummary(session);
      await refreshSessions(props.runtimeContext.documentId);
    } catch (error) {
      threadError.value = toThreadError(error);
    }
  }
}

async function submitInsertImage() {
  try {
    const result = await ElMessageBox.prompt("请输入待插入图的完整 URL", "插入网图", {
      confirmButtonText: "插入",
      cancelButtonText: "取消",
      inputPattern: /^https?:\/\/.+/,
      inputErrorMessage: "格式不正确，必须以 http:// 或 https:// 开头。"
    });
    if (result.value && result.value.trim()) {
      await emit("insert-image", result.value.trim());
    }
  } catch {
    // ignore cancel
  }
}

function handleCopy(text) {
  if (!text) {
    return;
  }
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      ElMessage.success("已复制到剪贴板");
    }).catch(() => {
      ElMessage.error("复制失败");
    });
  }
}

function handleRegenerate(entryIndex) {
  const entry = conversationEntries.value[entryIndex];
  if (!entry) {
    return;
  }
  void sendCurrentQuestion({
    retryConfirmed: true,
    retryPayload: {
      question: entry.question,
      selectionSnapshot: entry.selectionSnapshot,
      headingContext: entry.headingContext
    }
  });
}

function handleDeleteMessage(entryIndex) {
  ElMessageBox.confirm("确定删除这条问答?", "删除确认", {
    confirmButtonText: "删除",
    cancelButtonText: "取消",
    type: "warning"
  }).then(() => {
    conversationEntries.value.splice(entryIndex, 1);
    conversationEntries.value = [...conversationEntries.value];
  }).catch(() => {});
}

function handleOutlineCommand(node) {
  emit("jump-to-heading", node);
}

function renderAssistantText(entry) {
  return entry.assistantText || entry.streamingText || "";
}
</script>

<template>
  <div class="ai-workbench-shell">
    <div class="workbench-top-bar">
      <div class="top-actions">
        <el-tooltip content="历史会话" placement="bottom">
          <el-button text @click="drawerVisible = true">
            <el-icon><Menu /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="新建对话" placement="bottom">
          <el-button text @click="startNewChat">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
        <div class="workbench-status">{{ topStatusText }}</div>
      </div>
      <div class="session-title">当前会话：{{ currentSessionTitle }}</div>
    </div>

    <div v-if="capabilityStatus === 'capability-disabled'" class="capability-disabled">
      <p>llmAvailable=false</p>
      <p>disabledReason: {{ disabledReason }}</p>
      <p>输入区已禁用，请先打开后端 LLM 配置。</p>
    </div>

    <el-drawer v-model="drawerVisible" title="历史会话" direction="ltr" size="300px">
      <div class="session-list">
        <el-button
          v-for="session in sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: session.sessionId === currentSessionId }"
          plain
          @click="handleSessionClick(session.sessionId)"
        >
          <div class="session-info">
            <strong>{{ session.title }}</strong>
            <span>{{ session.updatedTime || session.documentId }}</span>
          </div>
        </el-button>
      </div>
    </el-drawer>

    <main class="thread-panel">
      <div v-if="threadError" class="thread-error-card">
        <strong>{{ threadError.errorCode || "ERROR" }}</strong>
        <span>{{ threadError.message }}</span>
      </div>

      <div class="toolbox">
        <div class="toolbox-actions">
          <el-button size="small" @click="submitInsertImage">
            <el-icon><Picture /></el-icon>
            <span>插入网图</span>
          </el-button>
          <el-button size="small" @click="handleManualCapture" :type="snapshotState === 'snapshot-ready' && !isExcludedSelection ? 'success' : 'default'">
            <el-icon><Crop /></el-icon>
            <span>{{ snapshotState === 'snapshot-ready' && !isExcludedSelection ? '已选择文本' : '选中文本' }}</span>
          </el-button>
          <el-button size="small" @click="emit('refresh-outline')">
            <el-icon><List /></el-icon>
            <span>刷新目录</span>
          </el-button>
          <el-dropdown @command="handleOutlineCommand" trigger="click">
            <el-button size="small">
              <span>跳转章节</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu style="max-height: calc(100vh - 280px); overflow-y: auto;">
                <el-dropdown-item
                  v-for="node in flatOutlineItems"
                  :key="node.id"
                  :command="node"
                  :style="{ paddingLeft: (12 + node.depth * 12) + 'px' }"
                >
                  <span class="outline-level-tag">H{{ node.level }}</span>{{ node.label || node.text }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="thread-list">
        <div v-for="(entry, index) in conversationEntries" :key="entry.key" class="thread-entry">
          <div class="bubble user-bubble">
            <p>{{ entry.question }}</p>
          </div>

          <div class="bubble assistant-bubble" :class="entry.status">
            <div class="panel-title-row" v-if="entry.status === 'failed'">
              <p class="bubble-label">请求失败</p>
              <el-button size="small" circle plain @click="openRetryDialog(entry)">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>

            <div v-if="renderAssistantText(entry)" class="markdown-body" v-html="md.render(renderAssistantText(entry))"></div>
            <p v-else class="assistant-placeholder">{{ entry.responseMessage }}</p>

            <div class="message-actions" v-if="entry.status !== 'failed'">
              <el-button size="small" text @click="handleCopy(renderAssistantText(entry))">
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
              <el-button size="small" text @click="handleRegenerate(index)">
                <el-icon><Refresh /></el-icon>
              </el-button>
              <el-button size="small" text type="danger" @click="handleDeleteMessage(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>

            <div class="meta-line">
              <span>errorCode: {{ entry.errorCode || "-" }}</span>
              <span>finishReason: {{ entry.finishReason || "-" }}</span>
              <span>provider: {{ entry.providerResponseMeta?.provider || "-" }}</span>
              <span>model: {{ entry.providerResponseMeta?.model || "-" }}</span>
            </div>
            <div class="meta-line" v-if="entry.usage?.totalTokens">
              <span>promptTokens: {{ entry.usage?.promptTokens ?? "-" }}</span>
              <span>completionTokens: {{ entry.usage?.completionTokens ?? "-" }}</span>
              <span>totalTokens: {{ entry.usage?.totalTokens ?? "-" }}</span>
            </div>
          </div>
        </div>
      </div>
    </main>

    <div class="composer-footer">
      <div class="composer">
        <div class="context-chips" v-if="runtimeContext.activeHeadingNode || (snapshotState === 'snapshot-ready' && !isExcludedSelection)">
          <el-tag size="small" type="info" v-if="runtimeContext.activeHeadingNode">
            <el-icon><Collection /></el-icon>
            {{ runtimeContext.activeHeadingNode.text || runtimeContext.activeHeadingNode.label }}
          </el-tag>
          <el-tag size="small" type="info" closable @close="handleRemoveSelection" v-if="snapshotState === 'snapshot-ready' && !isExcludedSelection">
            已获取选中文本片段
          </el-tag>
        </div>

        <div class="provider-row" v-if="providerOptions.length">
          <el-select v-model="selectedProvider" size="small" style="width: 180px;">
            <el-option
              v-for="option in providerOptions"
              :key="option.provider"
              :label="option.label || option.provider"
              :value="option.provider"
            />
          </el-select>
          <el-select v-model="selectedModel" size="small" style="width: 220px;">
            <el-option
              v-for="model in availableModels"
              :key="model"
              :label="model"
              :value="model"
            />
          </el-select>
        </div>

        <el-input
          v-model="draftQuestion"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="围绕当前选中文本提问..."
          :disabled="props.loading || props.closing || capabilityStatus === 'capability-disabled' || Boolean(currentRequestId)"
          resize="none"
        />

        <div class="composer-actions">
          <div>
            <el-button
              v-if="currentRequestId"
              size="small"
              circle
              @click="cancelSending"
              title="取消发送"
            >
              <el-icon><Close /></el-icon>
            </el-button>
            <el-button
              type="primary"
              size="small"
              circle
              :disabled="!canSend"
              @click="handleSendClick"
              title="发送问题"
            >
              <el-icon><Position /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showRetryDialog" class="dialog-mask">
      <div class="dialog-card">
        <h3>确认重试</h3>
        <p>question: {{ retryEntry?.question }}</p>
        <p>selectionSnapshot.text: {{ retryEntry?.selectionSnapshot?.text || "" }}</p>
        <p>selectionSnapshot.emptySelection: {{ retryEntry?.selectionSnapshot?.emptySelection }}</p>
        <p>headingContext.headingText: {{ retryEntry?.headingContext?.headingText || "" }}</p>
        <p>retryConfirmed: true</p>
        <div class="dialog-actions">
          <button type="button" class="ghost-button" @click="showRetryDialog = false">取消</button>
          <button type="button" class="primary-button" @click="confirmRetry">确认重试</button>
        </div>
      </div>
    </div>

    <div v-if="showSnapshotDecision" class="dialog-mask">
      <div class="dialog-card">
        <h3>检测到上下文变化</h3>
        <p>当前快照和当前线程不同，是否新开会话？</p>
        <div class="dialog-actions">
          <button type="button" class="primary-button" @click="confirmSnapshotDecision(true)">新开会话</button>
          <button type="button" class="ghost-button" @click="confirmSnapshotDecision(false)">继续当前会话</button>
          <button type="button" class="ghost-button" @click="showSnapshotDecision = false">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-workbench-shell {
  width: 800px;
  max-width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.workbench-top-bar,
.top-actions,
.toolbox-actions,
.composer-actions,
.dialog-actions,
.panel-title-row,
.message-actions,
.meta-line,
.provider-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workbench-top-bar,
.toolbox,
.composer,
.capability-disabled,
.thread-error-card,
.dialog-card {
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  border-radius: 8px;
}

.workbench-top-bar {
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 12px;
}

.workbench-status {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.session-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 50%;
}

.thread-panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbox,
.thread-error-card,
.capability-disabled {
  padding: 12px 16px;
}

.thread-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 0 16px 16px;
}

.thread-entry {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bubble {
  max-width: 100%;
}

.user-bubble {
  align-self: flex-end;
  background: var(--el-color-primary-light-9);
  border-radius: 8px 8px 0 8px;
  padding: 12px;
}

.user-bubble p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-bubble {
  padding: 0;
}

.assistant-placeholder {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.composer-footer {
  padding: 12px 16px 0;
}

.composer {
  padding: 12px;
}

.composer-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

.context-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  justify-content: flex-start;
  width: 100%;
}

.session-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  min-width: 0;
}

.session-info span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.outline-level-tag {
  display: inline-block;
  min-width: 22px;
  margin-right: 6px;
  font-size: 10px;
  text-align: center;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 30;
}

.dialog-card {
  width: min(520px, calc(100vw - 32px));
  padding: 20px;
}

.dialog-card h3,
.dialog-card p {
  margin-top: 0;
}

.dialog-actions {
  justify-content: flex-end;
}

.primary-button,
.ghost-button {
  border-radius: 8px;
  padding: 8px 14px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  cursor: pointer;
}

.primary-button {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}

@media (max-width: 1023px) {
  .ai-workbench-shell {
    width: 100vw;
  }

  .workbench-top-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .session-title {
    max-width: 100%;
  }
}
</style>
