<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import {
  cancelLlmRequest,
  createLlmSession,
  getLlmCapability,
  getLlmRequest,
  getLlmSession,
  listLlmSessions,
  sendLlmMessage
} from "./editorAiApi";

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

const pollIntervalMs = 1500;
const capabilityStatus = ref("bridge-pending");
const capability = ref(null);
const disabledReason = ref("");
const sessions = ref([]);
const showAllSessions = ref(false);
const currentSessionId = ref("");
const currentSessionTitle = ref("");
const currentSessionContextSignature = ref("");
const conversationEntries = ref([]);
const currentRequestId = ref("");
const currentRequestState = ref("");
const lastCancelled = ref(false);
const draftQuestion = ref("");
const imageUrl = ref("https://upload.wikimedia.org/wikipedia/commons/6/63/Wikipedia-logo.png");
const isInsertingImage = ref(false);
const threadError = ref(null);
const retryEntry = ref(null);
const showRetryDialog = ref(false);
const pendingSendMode = ref(null);
const showSnapshotDecision = ref(false);
const bootstrapRequestDocumentId = ref("");

let bootstrapToken = 0;
let sessionLoadToken = 0;
let pollTimer = null;

const displayedSessions = computed(() => (showAllSessions.value ? sessions.value : sessions.value.slice(0, 10)));
const canShowAllSessions = computed(() => sessions.value.length > 10 && !showAllSessions.value);
const snapshotState = computed(() => {
  if (!props.runtimeContext.bridgeReady) {
    return "bridge-pending";
  }
  return props.runtimeContext.hasEmptySelection || !props.runtimeContext.selectedText ? "snapshot-empty" : "snapshot-ready";
});
const liveContextSignature = computed(() => buildContextSignature({
  text: props.runtimeContext.selectedText,
  emptySelection: props.runtimeContext.hasEmptySelection,
  headingText: props.runtimeContext.activeHeadingNode?.text || props.runtimeContext.activeHeadingNode?.label || props.runtimeContext.activeHeadingNode?.headingText || ""
}));
const topStatusText = computed(() => {
  if (capabilityStatus.value === "capability-disabled") {
    return "模型暂不可用";
  }
  if (snapshotState.value === "bridge-pending") {
    return "正在准备上下文";
  }
  if (currentRequestState.value === "cancelling") {
    return "正在请求模型";
  }
  if (currentRequestState.value === "in_progress") {
    return "正在请求模型";
  }
  if (lastCancelled.value) {
    return "请求已取消";
  }
  return props.runtimeContext.bridgeStatusMessage || "等待上下文准备完成。";
});
const currentHeadingText = computed(() => props.runtimeContext.activeHeadingNode?.text || props.runtimeContext.activeHeadingNode?.label || "");

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

onBeforeUnmount(() => {
  clearPolling();
});

function resetWorkbench() {
  clearPolling();
  capabilityStatus.value = props.runtimeContext.bridgeReady ? "capability-loading" : "bridge-pending";
  capability.value = null;
  disabledReason.value = "";
  sessions.value = [];
  showAllSessions.value = false;
  currentSessionId.value = "";
  currentSessionTitle.value = "";
  currentSessionContextSignature.value = "";
  conversationEntries.value = [];
  currentRequestId.value = "";
  currentRequestState.value = "";
  lastCancelled.value = false;
  draftQuestion.value = "";
  threadError.value = null;
  retryEntry.value = null;
  showRetryDialog.value = false;
  pendingSendMode.value = null;
  showSnapshotDecision.value = false;
}

async function loadCapabilityAndBootstrap(documentId) {
  const token = ++bootstrapToken;
  capabilityStatus.value = props.runtimeContext.bridgeReady ? "capability-loading" : "bridge-pending";
  threadError.value = null;

  try {
    const capabilityPayload = await getLlmCapability(documentId);
    if (isBootstrapStale(token, documentId)) {
      return;
    }
    capability.value = capabilityPayload;
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

async function bootstrapSession(documentId, token) {
  bootstrapRequestDocumentId.value = documentId;
  const session = await createLlmSession(documentId);
  if (isBootstrapStale(token, documentId) || session.documentId !== props.runtimeContext.documentId || bootstrapRequestDocumentId.value !== documentId) {
    // stale response：文档已切换或 bootstrapRequestDocumentId 已失效，丢弃旧 documentId 的自动建会话结果。
    return;
  }
  applySessionSummary(session);
  await refreshSessions(documentId, token);
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

async function selectSession(sessionId) {
  const documentId = props.runtimeContext.documentId;
  if (!sessionId || !documentId) {
    return;
  }
  const token = ++sessionLoadToken;
  threadError.value = null;
  try {
    const session = await getLlmSession(sessionId, documentId);
    if (isSessionLoadStale(token, sessionId, documentId)) {
      return;
    }
    applySessionSummary(session);
    conversationEntries.value = buildConversationEntries(session.messages || []);
  } catch (error) {
    if (isSessionLoadStale(token, sessionId, documentId)) {
      return;
    }
    threadError.value = toThreadError(error);
    if (error.errorCode === "LLM_SESSION_NOT_FOUND" || error.errorCode === "LLM_SESSION_FORBIDDEN") {
      currentSessionId.value = "";
      conversationEntries.value = [];
      const fallbackSession = await createLlmSession(documentId);
      applySessionSummary(fallbackSession);
      await refreshSessions(documentId);
    }
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
        userCreatedTime: message.createdTime,
        assistantMessageId: "",
        requestId: "",
        status: "completed",
        assistantText: "",
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
      lastEntry.usage = message.usage || null;
      lastEntry.finishReason = message.finishReason || "";
      lastEntry.providerResponseMeta = message.providerResponseMeta || {};
      lastEntry.errorCode = message.errorCode || "";
      lastEntry.responseMessage = humanizeResponseState(message);
    } else {
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
        userCreatedTime: message.createdTime,
        assistantMessageId: message.messageId,
        requestId: "",
        status: message.status || "completed",
        assistantText: message.assistantText || "",
        usage: message.usage || null,
        finishReason: message.finishReason || "",
        providerResponseMeta: message.providerResponseMeta || {},
        errorCode: message.errorCode || "",
        responseMessage: humanizeResponseState(message)
      });
    }
  }
  return entries;
}

async function handleSendClick() {
  if (!draftQuestion.value.trim() || currentRequestId.value || capabilityStatus.value !== "capability-enabled") {
    return;
  }
  if (conversationEntries.value.length > 0 && currentSessionContextSignature.value && currentSessionContextSignature.value !== liveContextSignature.value) {
    pendingSendMode.value = "new-or-continue";
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

  lastCancelled.value = false;
  threadError.value = null;

  let targetSessionId = currentSessionId.value;
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
    question: retryPayload.question,
    selectionSnapshot: retryPayload.selectionSnapshot,
    headingContext: retryPayload.headingContext,
    retryConfirmed: true
  } : {
    documentId,
    sessionId: targetSessionId,
    question,
    selectionSnapshot: {
      text: props.runtimeContext.selectedText || "",
      emptySelection: Boolean(props.runtimeContext.hasEmptySelection || !props.runtimeContext.selectedText)
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
    userCreatedTime: new Date().toISOString(),
    assistantMessageId: "",
    requestId: "",
    status: "pending",
    assistantText: "",
    usage: null,
    finishReason: "",
    providerResponseMeta: {},
    errorCode: "",
    responseMessage: "等待模型返回..."
  };
  conversationEntries.value = [...conversationEntries.value, pendingEntry];
  currentSessionContextSignature.value = buildContextSignature({
    text: payload.selectionSnapshot.text,
    emptySelection: payload.selectionSnapshot.emptySelection,
    headingText: payload.headingContext.headingText
  });

  const result = await sendLlmMessage(payload);
  if (documentId !== props.runtimeContext.documentId || targetSessionId !== currentSessionId.value) {
    return;
  }

  pendingEntry.assistantMessageId = result.assistantMessageId || pendingEntry.assistantMessageId;
  pendingEntry.requestId = result.requestId || "";
  applyRequestResult(pendingEntry, result);
  await refreshSessions(documentId);

  if (result.status === "in_progress") {
    currentRequestId.value = result.requestId;
    currentRequestState.value = "in_progress";
    startPolling(result.requestId, targetSessionId, pendingEntry);
  } else {
    currentRequestId.value = "";
    currentRequestState.value = "";
  }

  if (!retryPayload) {
    draftQuestion.value = "";
  }
}

function startPolling(requestId, sessionId, pendingEntry) {
  clearPolling();
  currentRequestId.value = requestId;
  currentRequestState.value = "in_progress";
  pollTimer = window.setInterval(async () => {
    const documentId = props.runtimeContext.documentId;
    try {
      const result = await getLlmRequest(requestId, documentId);
      if (documentId !== props.runtimeContext.documentId || sessionId !== currentSessionId.value || requestId !== currentRequestId.value) {
        // stale response：documentId/sessionId/requestId 任一不匹配就直接丢弃。
        return;
      }
      applyRequestResult(pendingEntry, result);
      if (["completed", "failed", "cancelled"].includes(result.status)) {
        clearPolling();
        currentRequestId.value = "";
        currentRequestState.value = "";
      }
    } catch (error) {
      clearPolling();
      currentRequestId.value = "";
      currentRequestState.value = "";
      threadError.value = toThreadError(error);
    }
  }, pollIntervalMs);
}

async function cancelSending() {
  if (!currentRequestId.value) {
    return;
  }
  currentRequestState.value = "cancelling";
  const result = await cancelLlmRequest(currentRequestId.value, props.runtimeContext.documentId);
  const currentEntry = conversationEntries.value.find(entry => entry.requestId === result.requestId) || conversationEntries.value.at(-1);
  applyRequestResult(currentEntry, result);
  clearPolling();
  currentRequestId.value = "";
  currentRequestState.value = "";
  lastCancelled.value = true;
}

function applyRequestResult(entry, result) {
  if (!entry) {
    return;
  }
  entry.assistantMessageId = result.assistantMessageId || entry.assistantMessageId;
  entry.requestId = result.requestId || entry.requestId;
  entry.status = result.status;
  entry.assistantText = result.assistantText || "";
  entry.usage = result.usage || null;
  entry.finishReason = result.finishReason || "";
  entry.providerResponseMeta = result.providerResponseMeta || {};
  entry.errorCode = result.errorCode || "";
  entry.responseMessage = humanizeResult(result);
  conversationEntries.value = [...conversationEntries.value];
  if (result.status === "cancelled") {
    lastCancelled.value = true;
  }
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

function clearPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

function isBootstrapStale(token, documentId) {
  return token !== bootstrapToken || documentId !== props.runtimeContext.documentId;
}

function isSessionLoadStale(token, sessionId, documentId) {
  return token !== sessionLoadToken || sessionId !== currentSessionId.value && currentSessionId.value && documentId !== props.runtimeContext.documentId;
}

function toThreadError(error) {
  return {
    errorCode: error?.errorCode || "",
    message: error?.message || "请求失败"
  };
}

function buildContextSignature(context) {
  return JSON.stringify([
    context?.text || "",
    Boolean(context?.emptySelection),
    context?.headingText || ""
  ]);
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

async function submitInsertImage() {
  if (!imageUrl.value.trim()) {
    return;
  }
  isInsertingImage.value = true;
  try {
    await emit("insert-image", imageUrl.value.trim());
  } finally {
    isInsertingImage.value = false;
  }
}
</script>

<template>
  <div class="ai-workbench-shell">
    <div class="workbench-header">
      <div>
        <p class="eyebrow">AI 工作台</p>
        <h2 class="title">{{ documentTitle || runtimeContext.documentId }}</h2>
      </div>
      <div class="top-status" :class="[capabilityStatus, snapshotState]">
        {{ topStatusText }}
      </div>
    </div>

    <div v-if="capabilityStatus === 'capability-disabled'" class="capability-disabled">
      <p>llmAvailable=false</p>
      <p>disabledReason: {{ disabledReason }}</p>
      <p>输入区已禁用，请先打开后端 LLM 配置。</p>
    </div>

    <div class="workbench-grid">
      <aside class="session-panel">
        <div class="panel-title-row">
          <h3>最近 10 个会话</h3>
          <button type="button" class="ghost-button" @click="showAllSessions = !showAllSessions">
            {{ canShowAllSessions ? "查看全部会话" : "收起会话" }}
          </button>
        </div>
        <p class="eyebrow">当前会话：{{ currentSessionTitle || "未初始化" }}</p>
        <div class="session-list">
          <button
            v-for="session in displayedSessions"
            :key="session.sessionId"
            type="button"
            class="session-item"
            :class="{ active: session.sessionId === currentSessionId }"
            @click="selectSession(session.sessionId)"
          >
            <strong>{{ session.title }}</strong>
            <span>{{ session.updatedTime || session.documentId }}</span>
          </button>
        </div>
      </aside>

      <main class="thread-panel">
        <div class="context-toolbar">
          <div class="context-card">
            <div class="panel-title-row">
              <h3>当前选区</h3>
              <button type="button" class="ghost-button" @click="emit('capture-selection')">抓取当前选区</button>
            </div>
            <p class="snapshot-badge" :class="snapshotState">{{ snapshotState }}</p>
            <pre v-if="runtimeContext.selectedText" class="selection-preview">{{ runtimeContext.selectedText }}</pre>
            <p v-else>当前没有选中文本。</p>
          </div>

          <div class="context-card">
            <div class="panel-title-row">
              <h3>章节标题</h3>
              <button type="button" class="ghost-button" @click="emit('refresh-outline')">刷新目录</button>
            </div>
            <div class="outline-list">
              <button
                v-for="node in runtimeContext.outlineTreeData || []"
                :key="node.id"
                type="button"
                class="outline-item"
                @click="emit('jump-to-heading', node)"
              >
                {{ node.label || node.text }}
              </button>
            </div>
          </div>
        </div>

        <div v-if="threadError" class="thread-error-card">
          <strong>{{ threadError.errorCode || "ERROR" }}</strong>
          <span>{{ threadError.message }}</span>
        </div>

        <div class="thread-list">
          <div v-for="entry in conversationEntries" :key="entry.key" class="thread-entry">
            <div class="bubble user-bubble">
              <p class="bubble-label">用户问题</p>
              <p>{{ entry.question }}</p>
            </div>

            <div class="bubble assistant-bubble" :class="entry.status">
              <div class="panel-title-row">
                <p class="bubble-label">模型回复</p>
                <button
                  v-if="entry.status === 'failed'"
                  type="button"
                  class="ghost-button"
                  @click="openRetryDialog(entry)"
                >
                  重试
                </button>
              </div>
              <p v-if="entry.assistantText">{{ entry.assistantText }}</p>
              <p v-else>{{ entry.responseMessage }}</p>
              <div class="meta-line">
                <span>errorCode: {{ entry.errorCode || "-" }}</span>
                <span>finishReason: {{ entry.finishReason || "-" }}</span>
                <span>model: {{ entry.providerResponseMeta?.model || "-" }}</span>
              </div>
              <div class="meta-line">
                <span>promptTokens: {{ entry.usage?.promptTokens ?? "-" }}</span>
                <span>completionTokens: {{ entry.usage?.completionTokens ?? "-" }}</span>
                <span>totalTokens: {{ entry.usage?.totalTokens ?? "-" }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="composer">
          <textarea
            v-model="draftQuestion"
            class="composer-input"
            placeholder="围绕当前选区提问"
            :disabled="loading || closing || capabilityStatus === 'capability-disabled' || Boolean(currentRequestId)"
          />
          <div class="composer-actions">
            <button
              type="button"
              class="primary-button"
              :disabled="loading || closing || capabilityStatus === 'capability-disabled' || Boolean(currentRequestId)"
              @click="handleSendClick"
            >
              发送问题
            </button>
            <button
              v-if="currentRequestId"
              type="button"
              class="ghost-button"
              @click="cancelSending"
            >
              取消发送
            </button>
          </div>
        </div>

        <div class="image-panel">
          <h3>运行态 / 现有动作</h3>
          <input v-model="imageUrl" class="image-input" type="url" />
          <button type="button" class="ghost-button" :disabled="isInsertingImage" @click="submitInsertImage">
            {{ isInsertingImage ? "插入中..." : "在光标处插入网络图片" }}
          </button>
        </div>
      </main>
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
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 16px;
}

@media (max-width: 1439px) {
  .ai-workbench-shell {
    width: min(70vw, 800px);
  }
}

@media (max-width: 1023px) {
  .ai-workbench-shell {
    width: 100vw;
  }
}

.workbench-header,
.panel-title-row,
.composer-actions,
.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workbench-grid {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
  min-height: 0;
  flex: 1;
}

@media (max-width: 1023px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
}

.session-panel,
.thread-panel,
.context-card,
.capability-disabled,
.dialog-card,
.image-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-bg-color);
}

.session-panel,
.thread-panel,
.capability-disabled,
.dialog-card,
.image-panel {
  padding: 16px;
}

.thread-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.context-toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1023px) {
  .context-toolbar {
    grid-template-columns: 1fr;
  }
}

.context-card {
  padding: 12px;
}

.eyebrow,
.bubble-label,
.snapshot-badge,
.meta-line,
.session-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.title {
  margin: 4px 0 0;
  font-size: 20px;
}

.top-status {
  padding: 8px 12px;
  border-radius: 999px;
  background: var(--el-fill-color);
  font-size: 12px;
}

.capability-disabled {
  color: var(--el-color-danger);
}

.session-list,
.thread-list,
.outline-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item,
.outline-item,
.ghost-button,
.primary-button {
  border-radius: 10px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  padding: 10px 12px;
  cursor: pointer;
  text-align: left;
}

.session-item.active,
.primary-button {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.08);
}

.thread-list {
  flex: 1;
  overflow: auto;
}

.thread-entry {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bubble {
  border-radius: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
}

.assistant-bubble.failed {
  border: 1px solid var(--el-color-danger);
}

.assistant-bubble.cancelled {
  border: 1px solid var(--el-color-warning);
}

.selection-preview {
  white-space: pre-wrap;
  margin: 0;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.composer-input,
.image-input {
  width: 100%;
  min-height: 96px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color);
  padding: 12px;
  resize: vertical;
  font: inherit;
}

.image-input {
  min-height: 0;
}

.thread-error-card {
  border: 1px solid var(--el-color-danger);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dialog-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog-card {
  width: min(520px, calc(100vw - 32px));
}
</style>
