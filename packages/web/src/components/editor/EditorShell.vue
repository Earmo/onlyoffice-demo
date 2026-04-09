<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowLeft, ArrowRight } from "@element-plus/icons-vue";
import { DocumentEditor } from "@onlyoffice/document-editor-vue";
import { apiFetch, buildApiUrl, createAccessContextHeaders } from "../../lib/api";
import { createOnlyofficeBridge } from "./onlyofficeBridge";

const props = defineProps({
  documentId: {
    type: String,
    required: true
  },
  documentTitle: {
    type: String,
    default: ""
  },
  readonly: {
    type: Boolean,
    default: false
  },
  showConsole: {
    type: Boolean,
    default: true
  }
});

const imageUrl = ref("https://upload.wikimedia.org/wikipedia/commons/6/63/Wikipedia-logo.png");
const isConsoleOpen = ref(false);
const isLoading = ref(true);
const isInsertingImage = ref(false);
const errorMessage = ref("");
const editorPayload = ref(null);
const editorKey = ref(0);
const saveStatus = ref(null);
const editingSessionOpened = ref(false);
const isClosingSession = ref(false);
const selectedText = ref("");
const isCapturingSelection = ref(false);
const hasEmptySelection = ref(false);
const outlineItems = ref([]);
const isRefreshingOutline = ref(false);
const hasEmptyOutline = ref(false);
const bridgeErrorMessage = ref("");
const bridgeStatusMessage = ref("等待文档运行态桥接就绪。");
const bridgeReady = ref(false);
const bridgeCapability = ref("plugin");
const activeHeadingId = ref("");
const isRuntimeSectionExpanded = ref(true);
let saveStatusTimer = null;
let sessionHeartbeatTimer = null;
let closeEditingSessionPromise = null;
let removeUnloadListeners = null;
let onlyofficeBridge = null;

const modeLabel = computed(() => (props.readonly ? "预览模式" : "编辑模式"));
const shouldShowConsole = computed(() => props.showConsole && !props.readonly);
const bridgeStatusType = computed(() => {
  if (bridgeErrorMessage.value) {
    return "danger";
  }
  return bridgeReady.value ? "success" : "info";
});
const bridgeStatusLabel = computed(() => {
  if (bridgeErrorMessage.value) {
    return "桥接异常";
  }
  return bridgeReady.value ? "桥接已就绪" : "桥接连接中";
});
const bridgeCapabilityLabel = computed(() => (bridgeCapability.value === "connector" ? "connector + plugin" : "plugin"));

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

function getDocEditorInstance() {
  return window.DocEditor?.instances?.docEditor;
}

function getDocEditorIframe() {
  return document.getElementById("docEditor")?.querySelector("iframe") ?? null;
}

function resetBridgeState() {
  selectedText.value = "";
  hasEmptySelection.value = false;
  outlineItems.value = [];
  hasEmptyOutline.value = false;
  bridgeErrorMessage.value = "";
  bridgeStatusMessage.value = "等待文档运行态桥接就绪。";
  bridgeReady.value = false;
  bridgeCapability.value = "plugin";
  activeHeadingId.value = "";
}

function disposeBridge() {
  onlyofficeBridge?.dispose();
  onlyofficeBridge = null;
  resetBridgeState();
}

function ensureBridge() {
  if (props.readonly || !editorPayload.value) {
    return null;
  }
  if (!onlyofficeBridge) {
    onlyofficeBridge = createOnlyofficeBridge({
      getEditor: getDocEditorInstance,
      getIframe: getDocEditorIframe
    });
    bridgeCapability.value = onlyofficeBridge.capability;
  }
  return onlyofficeBridge;
}

function toBridgeErrorMessage(error, fallbackMessage) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallbackMessage;
}

async function waitForBridgeReady(options = {}) {
  const { suppressErrors = false } = options;
  const bridge = ensureBridge();
  if (!bridge) {
    return false;
  }
  if (bridgeReady.value) {
    return true;
  }

  bridgeStatusMessage.value = "正在连接文档运行态桥接...";
  bridgeErrorMessage.value = "";

  try {
    const payload = await bridge.waitForReady();
    bridgeReady.value = true;
    bridgeCapability.value = payload?.capability || bridge.capability;
    bridgeStatusMessage.value = "文档桥接已就绪，可读取选区并刷新章节目录。";
    return true;
  } catch (error) {
    bridgeReady.value = false;
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "文档桥接暂不可用，请稍后重试。");
    bridgeStatusMessage.value = "文档桥接未能成功建立。";
    if (!suppressErrors) {
      console.error("文档桥接初始化失败", error);
    }
    return false;
  }
}

async function captureSelectedText() {
  if (!(await waitForBridgeReady())) {
    return null;
  }

  isCapturingSelection.value = true;
  bridgeErrorMessage.value = "";

  try {
    const payload = await onlyofficeBridge.captureSelectedText();
    selectedText.value = payload.text ?? "";
    hasEmptySelection.value = Boolean(payload.emptySelection || selectedText.value.trim().length === 0);
    bridgeStatusMessage.value = hasEmptySelection.value
      ? "当前没有选中文本，可先在文档中框选一段内容。"
      : "已抓取当前选区，可作为下一阶段 AI 对话的上下文输入。";
    return payload;
  } catch (error) {
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "抓取当前选区失败，请稍后重试。");
    return null;
  } finally {
    isCapturingSelection.value = false;
  }
}

async function refreshOutline(options = {}) {
  const { silent = false } = options;
  if (!(await waitForBridgeReady({ suppressErrors: silent }))) {
    return null;
  }

  isRefreshingOutline.value = true;
  if (!silent) {
    bridgeErrorMessage.value = "";
  }

  try {
    const payload = await onlyofficeBridge.refreshOutline();
    outlineItems.value = Array.isArray(payload.headings) ? payload.headings : [];
    hasEmptyOutline.value = Boolean(payload.emptyOutline || outlineItems.value.length === 0);
    bridgeStatusMessage.value = hasEmptyOutline.value
      ? "当前文档没有检测到标题段落。"
      : "章节目录已刷新，可点击标题快速定位。";
    return payload;
  } catch (error) {
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "刷新章节目录失败，请稍后重试。");
    if (!silent) {
      outlineItems.value = [];
      hasEmptyOutline.value = false;
    }
    return null;
  } finally {
    isRefreshingOutline.value = false;
  }
}

async function jumpToHeading(heading) {
  if (!heading || !(await waitForBridgeReady())) {
    return null;
  }

  bridgeErrorMessage.value = "";

  try {
    const payload = await onlyofficeBridge.jumpToHeading(heading);
    activeHeadingId.value = heading.id;
    bridgeStatusMessage.value = `已定位到章节：${heading.text || heading.id}`;
    return payload;
  } catch (error) {
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "章节定位失败，请刷新目录后重试。");
    return null;
  }
}

async function loadEditorConfig() {
  isLoading.value = true;
  errorMessage.value = "";
  disposeBridge();

  try {
    const params = new URLSearchParams({
      readonly: String(props.readonly)
    });
    const response = await apiFetch(`/api/documents/${props.documentId}/editor-config?${params.toString()}`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `配置请求失败，HTTP ${response.status}`));
    }

    editorPayload.value = await response.json();
    editingSessionOpened.value = !props.readonly;
    if (!props.readonly) {
      startSessionHeartbeatPolling();
    }
    editorKey.value += 1;
    ensureBridge();

    if (shouldShowConsole.value) {
      await loadSaveStatus();
    } else {
      saveStatus.value = null;
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "未知错误";
    if (!props.readonly) {
      await closeEditingSession({ suppressErrors: true, force: true });
    }
  } finally {
    isLoading.value = false;
  }
}

async function fetchSaveStatusSnapshot(options = {}) {
  const { suppressErrors = false } = options;

  try {
    const response = await apiFetch(`/api/documents/${props.documentId}/save-status`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `状态请求失败，HTTP ${response.status}`));
    }
    return await response.json();
  } catch (error) {
    if (!suppressErrors) {
      throw error;
    }
    return null;
  }
}

async function loadSaveStatus() {
  if (!shouldShowConsole.value) {
    return;
  }

  try {
    const payload = await fetchSaveStatusSnapshot();
    if (payload) {
      saveStatus.value = payload;
    }
  } catch (error) {
    console.error("加载保存状态失败", error);
  }
}

function toggleConsole() {
  isConsoleOpen.value = !isConsoleOpen.value;
}

function closeConsole() {
  isConsoleOpen.value = false;
}

function toggleRuntimeSection() {
  isRuntimeSectionExpanded.value = !isRuntimeSectionExpanded.value;
}

async function insertRemoteImage() {
  if (props.readonly) {
    errorMessage.value = "预览模式下不能插入图片。";
    return;
  }

  const editor = getDocEditorInstance();
  if (!editor) {
    errorMessage.value = "编辑器尚未准备完成，请稍后再试。";
    return;
  }

  isInsertingImage.value = true;
  errorMessage.value = "";

  try {
    const response = await apiFetch(`/api/documents/${props.documentId}/images/insert`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        sourceUrl: imageUrl.value
      })
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `插图配置请求失败，HTTP ${response.status}`));
    }

    const payload = await response.json();
    editor.insertImage(payload.insertImage);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "插入图片失败";
  } finally {
    isInsertingImage.value = false;
  }
}

function handleDocumentReady() {
  startSessionHeartbeatPolling();
  if (shouldShowConsole.value) {
    startSaveStatusPolling();
    void nextTick(async () => {
      ensureBridge();
      const ready = await waitForBridgeReady({ suppressErrors: true });
      if (ready) {
        await refreshOutline({ silent: true });
      }
    });
  }
  openNavigationPanelAfterReady();
}

function openNavigationPanelAfterReady() {
  setTimeout(() => {
    try {
      const iframe = getDocEditorIframe();
      if (!iframe) {
        return;
      }

      const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
      if (!iframeDoc) {
        return;
      }

      const navBtn = iframeDoc.getElementById("left-btn-navigation");
      if (navBtn && !navBtn.classList.contains("active") && !navBtn.classList.contains("pressed")) {
        navBtn.click();
      }
    } catch {
      // 同源判断失败或按钮不存在时静默降级，不影响编辑器正常使用
    }
  }, 800);
}

function handleLoadComponentError(errorCode, errorDescription) {
  disposeBridge();
  errorMessage.value = `ONLYOFFICE 组件加载失败（${errorCode}）：${errorDescription}`;
}

function startSaveStatusPolling() {
  stopSaveStatusPolling();
  saveStatusTimer = window.setInterval(() => {
    loadSaveStatus();
  }, 5000);
}

function stopSaveStatusPolling() {
  if (saveStatusTimer !== null) {
    window.clearInterval(saveStatusTimer);
    saveStatusTimer = null;
  }
}

async function touchEditingSession(options = {}) {
  const {
    keepalive = false,
    suppressErrors = false
  } = options;

  if (props.readonly || !editingSessionOpened.value) {
    return;
  }

  try {
    const response = await apiFetch(`/api/documents/${props.documentId}/editing-sessions/heartbeat`, {
      method: "POST",
      keepalive
    });
    if (!response.ok && !suppressErrors) {
      throw new Error(await readErrorMessage(response, `刷新编辑会话失败，HTTP ${response.status}`));
    }
  } catch (error) {
    if (!suppressErrors) {
      throw error;
    }
  }
}

function startSessionHeartbeatPolling() {
  stopSessionHeartbeatPolling();
  if (props.readonly) {
    return;
  }

  sessionHeartbeatTimer = window.setInterval(() => {
    void touchEditingSession({ suppressErrors: true });
  }, 5000);
}

function stopSessionHeartbeatPolling() {
  if (sessionHeartbeatTimer !== null) {
    window.clearInterval(sessionHeartbeatTimer);
    sessionHeartbeatTimer = null;
  }
}

function destroyDocEditor() {
  try {
    const editor = getDocEditorInstance();
    if (editor && typeof editor.destroyEditor === "function") {
      editor.destroyEditor();
    }
  } catch {
    // 编辑器实例可能已被清理，静默忽略
  }
}

async function closeEditingSession(options = {}) {
  const {
    keepalive = false,
    suppressErrors = false,
    force = false
  } = options;

  if (props.readonly) {
    return null;
  }
  if (!editingSessionOpened.value && !force) {
    return null;
  }
  if (closeEditingSessionPromise) {
    try {
      return await closeEditingSessionPromise;
    } catch (error) {
      if (!suppressErrors) {
        throw error;
      }
      return null;
    }
  }

  stopSaveStatusPolling();
  stopSessionHeartbeatPolling();

  isClosingSession.value = true;
  closeEditingSessionPromise = (async () => {
    if (!keepalive) {
      const saveResponse = await apiFetch(`/api/documents/${props.documentId}/save`, {
        method: "POST"
      });
      if (!saveResponse.ok) {
        throw new Error(await readErrorMessage(saveResponse, `保存文档失败，HTTP ${saveResponse.status}`));
      }
      saveStatus.value = await saveResponse.json();
    }

    destroyDocEditor();

    const response = await apiFetch(`/api/documents/${props.documentId}/editing-sessions/close`, {
      method: "POST",
      keepalive
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `结束编辑会话失败，HTTP ${response.status}`));
    }

    const payload = await response.json();
    editingSessionOpened.value = false;
    saveStatus.value = payload;
    return payload;
  })();

  try {
    return await closeEditingSessionPromise;
  } catch (error) {
    if (!suppressErrors) {
      throw error;
    }
    return null;
  } finally {
    isClosingSession.value = false;
    closeEditingSessionPromise = null;
  }
}

function dispatchUnloadCloseRequest() {
  if (props.readonly || !editingSessionOpened.value || closeEditingSessionPromise) {
    return;
  }

  editingSessionOpened.value = false;
  stopSaveStatusPolling();
  stopSessionHeartbeatPolling();

  fetch(buildApiUrl(`/api/documents/${props.documentId}/editing-sessions/close`), {
    method: "POST",
    keepalive: true,
    headers: createAccessContextHeaders()
  }).catch(() => {});
}

function formatTimestamp(value) {
  if (!value) {
    return "暂无";
  }

  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "medium"
  }).format(new Date(value));
}

function saveStatusTone(state) {
  return {
    "is-idle": state === "idle",
    "is-progress": state === "callback-received" || state === "editing",
    "is-success": state === "saved",
    "is-error": state === "save-failed" || state === "failed"
  };
}

watch(
  () => [props.documentId, props.readonly, props.showConsole],
  async () => {
    disposeBridge();
    stopSaveStatusPolling();
    stopSessionHeartbeatPolling();
    saveStatus.value = null;
    editingSessionOpened.value = false;
    if (!props.showConsole || props.readonly) {
      isConsoleOpen.value = false;
    }
    await loadEditorConfig();
  },
  { immediate: true }
);

onMounted(() => {
  const handlePageHide = () => {
    dispatchUnloadCloseRequest();
  };
  window.addEventListener("pagehide", handlePageHide);
  window.addEventListener("beforeunload", handlePageHide);
  removeUnloadListeners = () => {
    window.removeEventListener("pagehide", handlePageHide);
    window.removeEventListener("beforeunload", handlePageHide);
  };
});

onBeforeUnmount(async () => {
  disposeBridge();
  stopSaveStatusPolling();
  stopSessionHeartbeatPolling();
  removeUnloadListeners?.();
  await closeEditingSession({ keepalive: true, suppressErrors: true });
});

defineExpose({
  closeEditingSession,
  captureSelectedText,
  refreshOutline
});
</script>

<template>
  <el-container class="editor-workspace">
    <el-main class="editor-stage-stack">
      <el-empty v-if="isLoading" description="正在获取编辑器配置..." />

      <el-alert v-else-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" style="margin: 16px;">
        <p class="hint">
          请确认当前站点的 <code>/api</code> 反向代理可用，并且 ONLYOFFICE 相关路径已通过同源方式转发。
        </p>
      </el-alert>

      <div v-else-if="editorPayload" class="editor-shell">
        <DocumentEditor
          :key="editorKey"
          id="docEditor"
          :documentServerUrl="editorPayload.documentServerUrl"
          :config="editorPayload.config"
          height="100%"
          width="100%"
          :events_onDocumentReady="handleDocumentReady"
          :onLoadComponentError="handleLoadComponentError"
        />
      </div>

      <div
        v-if="shouldShowConsole && !isConsoleOpen"
        class="stage-edge-toggle"
        title="打开 AI 对话工作台"
        @click="toggleConsole"
      >
        <el-icon><ArrowLeft /></el-icon>
      </div>
    </el-main>

    <div v-show="shouldShowConsole && isConsoleOpen" class="floating-console">
      <div class="console-panel-header">
        <div style="flex: 1;">
          <p class="eyebrow">AI 对话准备态</p>
          <h2 class="title">{{ props.documentTitle || props.documentId }}</h2>
          <p class="summary">
            当前页面已进入 AI-ready 编辑工作台，可先抓取选区、刷新章节目录并快速定位内容。
          </p>
        </div>
        <el-button class="panel-close" circle @click="closeConsole">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>

      <div class="console-body">
        <el-card shadow="never" class="panel-section">
          <template #header>
            <div class="panel-section-header">
              <span>当前选区</span>
              <el-button
                size="small"
                :loading="isCapturingSelection"
                :disabled="isLoading || isClosingSession"
                @click="captureSelectedText"
              >
                {{ isCapturingSelection ? "抓取中..." : "抓取当前选区" }}
              </el-button>
            </div>
          </template>

          <div class="bridge-status-row">
            <el-tag size="small" :type="bridgeStatusType">{{ bridgeStatusLabel }}</el-tag>
            <span class="bridge-capability">{{ bridgeCapabilityLabel }}</span>
          </div>
          <p class="panel-hint">{{ bridgeStatusMessage }}</p>
          <el-alert
            v-if="bridgeErrorMessage"
            :title="bridgeErrorMessage"
            type="error"
            show-icon
            :closable="false"
            class="panel-inline-alert"
          />

          <div v-if="selectedText" class="selection-preview">
            <pre>{{ selectedText }}</pre>
          </div>
          <el-empty
            v-else-if="hasEmptySelection"
            description="当前没有选中文本，可先在文档中框选一段内容后再抓取。"
            :image-size="72"
          />
          <p v-else class="panel-hint">
            点击“抓取当前选区”后，这里会展示可直接进入 AI 对话窗口的文本上下文。
          </p>
        </el-card>

        <el-card shadow="never" class="panel-section">
          <template #header>
            <div class="panel-section-header">
              <span>章节标题</span>
              <el-button
                size="small"
                :loading="isRefreshingOutline"
                :disabled="isLoading || isClosingSession"
                @click="refreshOutline"
              >
                {{ isRefreshingOutline ? "刷新中..." : "刷新目录" }}
              </el-button>
            </div>
          </template>

          <div v-if="outlineItems.length" class="outline-list">
            <button
              v-for="heading in outlineItems"
              :key="heading.id"
              type="button"
              class="outline-item"
              :class="{ active: activeHeadingId === heading.id }"
              @click="jumpToHeading(heading)"
            >
              <span class="outline-level">H{{ heading.level }}</span>
              <span class="outline-copy">
                <strong>{{ heading.text || "未命名标题" }}</strong>
                <small>{{ heading.styleName || `段落 ${heading.paragraphIndex}` }}</small>
              </span>
            </button>
          </div>
          <el-empty
            v-else-if="hasEmptyOutline"
            description="当前文档还没有检测到标题段落。"
            :image-size="72"
          />
          <p v-else class="panel-hint">
            点击“刷新目录”后，这里会显示文档中的章节标题，并支持快速定位。
          </p>
        </el-card>

        <el-card shadow="never" class="panel-section">
          <template #header>
            <div class="panel-section-header">
              <span>运行态 / 现有动作</span>
              <el-button size="small" text @click="toggleRuntimeSection">
                {{ isRuntimeSectionExpanded ? "收起" : "展开" }}
              </el-button>
            </div>
          </template>

          <div v-if="isRuntimeSectionExpanded">
            <p class="panel-document-title">{{ props.documentTitle || "未命名文档" }}</p>
            <p class="panel-document-meta">documentId: <code>{{ props.documentId }}</code></p>
            <p class="panel-document-meta">当前模式：<el-tag size="small">{{ modeLabel }}</el-tag></p>

            <div class="console-inline-actions">
              <el-tag type="info">{{ modeLabel }}</el-tag>
              <el-button size="small" :disabled="isLoading" @click="loadEditorConfig">
                重新加载配置
              </el-button>
            </div>

            <div v-if="saveStatus" class="runtime-block">
              <p class="runtime-title">最近保存状态</p>
              <div class="save-status-card" :class="saveStatusTone(saveStatus.state)">
                <p class="save-status-headline" style="font-weight: bold; margin-bottom: 8px;">{{ saveStatus.message }}</p>
                <p class="save-status-meta">
                  最近回调状态码：<code>{{ saveStatus.lastCallbackStatus ?? "暂无" }}</code>
                </p>
                <p class="save-status-meta">
                  最近回调时间：<code>{{ formatTimestamp(saveStatus.lastCallbackTime) }}</code>
                </p>
                <p class="save-status-meta">
                  最近成功落盘：<code>{{ formatTimestamp(saveStatus.lastSavedTime) }}</code>
                </p>
              </div>
              <ul v-if="saveStatus.recentEvents?.length" class="save-status-events">
                <li v-for="event in saveStatus.recentEvents" :key="`${event.eventType}-${event.eventTime}`">
                  <strong>{{ event.eventType }}</strong>
                  <span>{{ event.message }}</span>
                  <time>{{ formatTimestamp(event.eventTime) }}</time>
                </li>
              </ul>
              <el-button style="margin-top: 12px;" @click="loadSaveStatus">
                刷新保存状态
              </el-button>
            </div>

            <div class="runtime-block">
              <p class="runtime-title">在光标处插入网络图片</p>
              <el-form label-position="top">
                <el-form-item label="网络图片地址">
                  <el-input
                    v-model="imageUrl"
                    type="url"
                    placeholder="https://example.com/demo.png"
                    :disabled="isLoading || isInsertingImage"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    :disabled="isLoading || isInsertingImage || isClosingSession"
                    @click="insertRemoteImage"
                  >
                    {{ isInsertingImage ? "插入中..." : "在光标处插入网络图片" }}
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </el-container>
</template>

<style scoped>
.editor-workspace {
  display: flex;
  flex-direction: row;
  height: 100vh;
  min-height: 0;
  background: var(--el-bg-color-page);
}

.editor-stage-stack {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  padding: 0;
}

.editor-shell {
  flex: 1;
  min-height: 0;
}

.editor-shell > div {
  height: 100%;
}

.stage-edge-toggle {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 48px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-right: none;
  border-radius: 4px 0 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 100;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.05);
}

.floating-console {
  width: 400px;
  max-width: 100vw;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color);
  display: flex;
  flex-direction: column;
}

.console-panel-header {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0;
}

.title {
  margin: 8px 0;
  font-size: 20px;
}

.summary {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin: 0;
}

.console-body {
  flex: 1;
  overflow: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-section {
  --el-card-padding: 16px;
}

.panel-section-header,
.console-inline-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.console-inline-actions {
  margin: 16px 0;
}

.panel-document-title,
.panel-document-meta {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.panel-document-title {
  font-weight: bold;
}

.panel-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.panel-inline-alert {
  margin-top: 12px;
}

.bridge-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bridge-capability {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.selection-preview {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  max-height: 220px;
  overflow: auto;
}

.selection-preview pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.6;
}

.outline-list {
  display: grid;
  gap: 8px;
}

.outline-item {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: var(--el-fill-color-blank);
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.outline-item:hover,
.outline-item.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.outline-level {
  display: inline-flex;
  min-width: 28px;
  justify-content: center;
  border-radius: 999px;
  background: var(--el-fill-color);
  padding: 2px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.outline-copy {
  display: grid;
  gap: 4px;
}

.outline-copy strong {
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.outline-copy small {
  color: var(--el-text-color-secondary);
}

.runtime-block + .runtime-block {
  margin-top: 20px;
}

.runtime-title {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.save-status-card {
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  margin-bottom: 12px;
}

.save-status-meta {
  margin: 4px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.save-status-events {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}

.save-status-events li {
  padding: 8px 12px;
  background: var(--el-fill-color);
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.save-status-events strong {
  color: var(--el-color-primary);
}

.save-status-events time {
  color: var(--el-text-color-secondary);
}
</style>
