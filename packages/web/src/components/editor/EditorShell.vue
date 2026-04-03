<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowLeft, ArrowRight } from "@element-plus/icons-vue";
import { DocumentEditor } from "@onlyoffice/document-editor-vue";
import { apiFetch, buildApiUrl, createAccessContextHeaders } from "../../lib/api";

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

// 组件只管理“当前文档在当前页面中的运行态”：
// 1. 请求 editor-config；
// 2. 承载 ONLYOFFICE 编辑器实例；
// 3. 在编辑模式下轮询 save-status；
// 4. 在页面离开或切换文档时，向后端显式结束当前编辑会话。
//
// “保存并返回”当前保持轻量时序：
// - 先调用 /save，确保本次显式保存已经落盘；
// - 再销毁编辑器并关闭编辑会话；
// - 不额外轮询 save-status 等待收敛，避免返回动作卡顿。
// 关闭后如果 ONLYOFFICE 继续补发 status=4 callback，由后端负责把无活跃会话的文档状态收口回稳定态。
// Phase 9 的这一版还恢复了“右侧悬浮按钮 -> 固定控制台面板”的交互，
// 避免编辑器顶部再出现额外一层 shell-toolbar，减少对主工作区的挤压。
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
let saveStatusTimer = null;
let sessionHeartbeatTimer = null;
let closeEditingSessionPromise = null;
let removeUnloadListeners = null;

const modeLabel = computed(() => (props.readonly ? "预览模式" : "编辑模式"));
const shouldShowConsole = computed(() => props.showConsole && !props.readonly);

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

async function loadEditorConfig() {
  isLoading.value = true;
  errorMessage.value = "";

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

function getDocEditorInstance() {
  return window.DocEditor?.instances?.docEditor;
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
  }
  // OnlyOffice 社区版的 layout.leftMenu.mode 配置不生效（需要 White Label 许可证），
  // 也无公开 JS API 可控制导航面板的初始展开状态。
  // 由于 nginx 将 OnlyOffice 路径全部代理到同源，iframe 为同源，
  // 可在文档加载完成后直接操作 iframe DOM，模拟用户点击导航按钮展开标题面板。
  openNavigationPanelAfterReady();
}

function openNavigationPanelAfterReady() {
  // 等待 OnlyOffice iframe 完成内部 UI 初始化（通常需要 500ms 以上）
  setTimeout(() => {
    try {
      const iframe = document.getElementById("docEditor")?.querySelector("iframe");
      if (!iframe) return;

      const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
      if (!iframeDoc) return;

      // 按钮 ID 来自 OnlyOffice 源码 LeftMenu.js：
      //   this.btnNavigation = new Common.UI.Button({ el: $markup.elementById('#left-btn-navigation') })
      // 按钮未被按下时点击可展开导航面板；已激活则不重复点击
      const navBtn = iframeDoc.getElementById("left-btn-navigation");
      if (navBtn && !navBtn.classList.contains("active") && !navBtn.classList.contains("pressed")) {
        navBtn.click();
      }
    } catch (e) {
      // 同源判断失败或按钮不存在时静默降级，不影响编辑器正常使用
    }
  }, 800);
}

function handleLoadComponentError(errorCode, errorDescription) {
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

/**
 * 销毁 ONLYOFFICE 编辑器实例。
 *
 * 调用后 ONLYOFFICE Document Server 会感知到客户端断连，
 * 当所有客户端都断开后会触发 callback status 2（保存并关闭）。
 */
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
    // 显式点击“保存并返回”时，先走后端 Command Service 的 forcesave + await，
    // 保证这次用户主动触发的保存已经完成，再继续销毁编辑器和关闭会话。
    if (!keepalive) {
      const saveResponse = await apiFetch(`/api/documents/${props.documentId}/save`, {
        method: "POST"
      });
      if (!saveResponse.ok) {
        throw new Error(await readErrorMessage(saveResponse, `保存文档失败，HTTP ${saveResponse.status}`));
      }
      saveStatus.value = await saveResponse.json();
    }

    // 页面继续留在编辑页时，再主动销毁编辑器实例（断开与 DS 的连接）。
    destroyDocEditor();

    // 最后通知后端关闭编辑会话记录。
    // 如果销毁编辑器后 ONLYOFFICE 还会补发关闭类 callback，
    // 由后端按“是否还有活跃编辑会话”决定是否继续投影为 editing。
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
  stopSaveStatusPolling();
  stopSessionHeartbeatPolling();
  removeUnloadListeners?.();
  await closeEditingSession({ keepalive: true, suppressErrors: true });
});

defineExpose({
  closeEditingSession
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

      <!-- Arrow toggle -->
      <div 
        v-if="shouldShowConsole && !isConsoleOpen" 
        class="stage-edge-toggle" 
        title="打开控制台"
        @click="toggleConsole"
      >
        <el-icon><ArrowLeft /></el-icon>
      </div>
    </el-main>

    <div
      v-show="shouldShowConsole && isConsoleOpen"
      class="floating-console"
    >
      <div class="console-panel-header">
        <div style="flex: 1;">
          <p class="eyebrow">编辑运行态</p>
          <h2 class="title">{{ props.documentTitle || props.documentId }}</h2>
          <p class="summary">
            {{ props.readonly ? "当前页面以只读预览方式打开文档，不建立活跃编辑会话。" : "当前页面已进入可编辑工作台，离开页面前会显式结束当前编辑会话。" }}
          </p>
        </div>
        <el-button class="panel-close" circle @click="closeConsole">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>

      <div class="console-body">
        <el-card shadow="never" class="panel-section">
          <template #header>当前文档</template>
          <p class="panel-document-title">{{ props.documentTitle || "未命名文档" }}</p>
          <p class="panel-document-meta">documentId: <code>{{ props.documentId }}</code></p>
          <p class="panel-document-meta">当前模式：<el-tag size="small">{{ modeLabel }}</el-tag></p>
        </el-card>

        <el-card v-if="saveStatus" shadow="never" class="panel-section">
          <template #header>最近保存状态</template>
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
        </el-card>

        <el-card shadow="never" class="panel-section">
          <template #header>编辑动作</template>
          <div class="console-inline-actions" style="margin-bottom: 16px;">
            <el-tag type="info" style="margin-right: 8px;">{{ modeLabel }}</el-tag>
            <el-button size="small" :disabled="isLoading" @click="loadEditorConfig">
              重新加载配置
            </el-button>
          </div>
          
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
  padding: 0; /* Override el-main padding for full editor */
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

.panel-document-title,
.panel-document-meta {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.panel-document-title {
  font-weight: bold;
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
