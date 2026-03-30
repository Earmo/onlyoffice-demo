<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { DocumentEditor } from "@onlyoffice/document-editor-vue";
import { apiFetch } from "../../lib/api";

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
let saveStatusTimer = null;

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
    const response = await apiFetch(`/api/documents/${props.documentId}/save-status`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `状态请求失败，HTTP ${response.status}`));
    }
    saveStatus.value = await response.json();
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

  try {
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
  } catch (error) {
    if (!suppressErrors) {
      throw error;
    }
    return null;
  }
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
    saveStatus.value = null;
    editingSessionOpened.value = false;
    if (!props.showConsole || props.readonly) {
      isConsoleOpen.value = false;
    }
    await loadEditorConfig();
  },
  { immediate: true }
);

onBeforeUnmount(async () => {
  stopSaveStatusPolling();
  await closeEditingSession({ keepalive: true, suppressErrors: true });
});

defineExpose({
  closeEditingSession
});
</script>

<template>
  <section class="editor-workspace">
    <section class="editor-stage-stack">
      <section v-if="isLoading" class="state-card">
        <p>正在获取编辑器配置...</p>
      </section>

      <section v-else-if="errorMessage" class="state-card error">
        <p>{{ errorMessage }}</p>
        <p class="hint">
          请确认当前站点的 <code>/api</code> 反向代理可用，并且 ONLYOFFICE 相关路径已通过同源方式转发。
        </p>
      </section>

      <section v-else-if="editorPayload" class="editor-shell">
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
      </section>
    </section>

    <button
      v-if="shouldShowConsole && !isConsoleOpen"
      class="stage-edge-toggle"
      type="button"
      title="打开控制台"
      @click="toggleConsole"
    >▶</button>

    <aside
      v-if="shouldShowConsole"
      class="side-panel floating-console"
      :class="{ open: isConsoleOpen }"
      :style="isConsoleOpen ? {} : { width: '0', opacity: '0', overflow: 'hidden', padding: '0' }"
      aria-label="编辑器控制台"
    >
      <div class="console-panel-header">
        <div>
          <p class="eyebrow">编辑运行态</p>
          <h2>{{ props.documentTitle || props.documentId }}</h2>
          <p class="summary">
            {{ props.readonly ? "当前页面以只读预览方式打开文档，不建立活跃编辑会话。" : "当前页面已进入可编辑工作台，离开页面前会显式结束当前编辑会话。" }}
          </p>
        </div>
        <button class="panel-close" type="button" @click="closeConsole">
          收起控制台
        </button>
      </div>

      <div class="console-body">
        <section class="panel-section">
          <p class="panel-section-title">当前文档</p>
          <p class="panel-document-title">{{ props.documentTitle || "未命名文档" }}</p>
          <p class="panel-document-meta">documentId: <code>{{ props.documentId }}</code></p>
          <p class="panel-document-meta">当前模式：<code>{{ modeLabel }}</code></p>
        </section>

        <section v-if="saveStatus" class="panel-section">
          <p class="panel-section-title">最近保存状态</p>
          <div class="save-status-card" :class="saveStatusTone(saveStatus.state)">
            <p class="save-status-headline">{{ saveStatus.message }}</p>
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
          <button class="ghost-button secondary compact" type="button" @click="loadSaveStatus">
            刷新保存状态
          </button>
        </section>

        <section class="panel-section">
          <p class="panel-section-title">编辑动作</p>
          <div class="console-inline-actions">
            <span class="status-chip is-outline">{{ modeLabel }}</span>
            <button class="ghost-button secondary compact" type="button" :disabled="isLoading" @click="loadEditorConfig">
              重新加载配置
            </button>
          </div>
          <label class="field-grid">
            <span>网络图片地址</span>
            <input
              v-model="imageUrl"
              class="surface-input"
              type="url"
              placeholder="https://example.com/demo.png"
              :disabled="isLoading || isInsertingImage"
            />
          </label>
          <button
            class="ghost-button accent"
            type="button"
            :disabled="isLoading || isInsertingImage"
            @click="insertRemoteImage"
          >
            {{ isInsertingImage ? "插入中..." : "在光标处插入网络图片" }}
          </button>
        </section>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.editor-workspace {
  display: flex;
  flex-direction: row;
  height: 100%;
  min-height: 0;
}

.editor-stage-stack {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.editor-shell {
  flex: 1;
  min-height: 0;
}


.editor-shell > div {
  height: 100%;
}

.floating-console {
  grid-template-rows: auto minmax(0, 1fr);
}

.console-panel-header {
  display: grid;
  gap: 14px;
}

.console-panel-header h2 {
  margin: 8px 0;
  font-size: clamp(24px, 2.4vw, 34px);
}

.console-body {
  display: grid;
  gap: 12px;
  align-content: start;
  overflow: auto;
  padding-right: 4px;
}

.field-grid {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: var(--muted-strong);
}

.save-status-events {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.save-status-events li {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
}

.save-status-events strong {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--accent-main);
}

.save-status-events span,
.save-status-events time {
  font-size: 12px;
  color: var(--muted-strong);
}

.console-inline-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}


.stage-edge-toggle {
  flex-shrink: 0;
  width: 32px;
  align-self: stretch;
  border: 1px solid var(--surface-border);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--muted-strong);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 160ms ease;
}


@media (max-width: 760px) {
  .console-panel-header h2 {
    font-size: 26px;
  }
}
</style>
