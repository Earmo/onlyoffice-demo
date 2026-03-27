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
const imageUrl = ref("https://upload.wikimedia.org/wikipedia/commons/6/63/Wikipedia-logo.png");
const isConsoleCollapsed = ref(false);
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
  isConsoleCollapsed.value = !isConsoleCollapsed.value;
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
  async (_newValue, _oldValue) => {
    stopSaveStatusPolling();
    saveStatus.value = null;
    editingSessionOpened.value = false;
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
  <section
    class="editor-workspace"
    :class="{
      'with-console': shouldShowConsole,
      'console-collapsed': shouldShowConsole && isConsoleCollapsed
    }"
  >
    <header class="surface-panel shell-toolbar">
      <div class="toolbar-copy">
        <p class="eyebrow">编辑运行态</p>
        <h2>{{ props.documentTitle || props.documentId }}</h2>
        <p class="summary">
          {{ props.readonly ? "当前页面以只读预览方式打开文档，不建立活跃编辑会话。" : "当前页面已进入可编辑工作台，离开页面前会显式结束当前编辑会话。" }}
        </p>
      </div>

      <div class="toolbar-actions">
        <span class="status-chip is-outline">{{ modeLabel }}</span>
        <button class="ghost-button secondary compact" type="button" :disabled="isLoading" @click="loadEditorConfig">
          重新加载配置
        </button>
        <button
          v-if="shouldShowConsole"
          class="ghost-button compact"
          type="button"
          :disabled="isLoading"
          @click="toggleConsole"
        >
          {{ isConsoleCollapsed ? "展开控制台" : "收起控制台" }}
        </button>
      </div>
    </header>

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

    <aside v-if="shouldShowConsole" class="side-panel docked-console" aria-label="编辑器控制台">
      <div v-if="!isConsoleCollapsed" class="console-body">
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

      <div v-else class="collapsed-rail">
        <button class="ghost-button compact" type="button" @click="toggleConsole">
          展开控制台
        </button>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.editor-workspace {
  display: grid;
  gap: 14px;
  min-height: 0;
  height: 100%;
}

.editor-workspace.with-console {
  grid-template-columns: minmax(0, 1fr) 360px;
  grid-template-areas:
    "toolbar toolbar"
    "stage console";
}

.editor-workspace.with-console.console-collapsed {
  grid-template-columns: minmax(0, 1fr) 96px;
}

.shell-toolbar {
  grid-area: toolbar;
  position: sticky;
  top: 0;
  z-index: 6;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-end;
}

.toolbar-copy h2 {
  margin: 8px 0;
  font-size: clamp(24px, 2.4vw, 34px);
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.editor-stage-stack {
  grid-area: stage;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-shell {
  overflow: hidden;
  min-height: calc(100vh - 310px);
}

.editor-shell > div {
  height: 100%;
}

.docked-console {
  grid-area: console;
  position: sticky;
  top: 112px;
  align-self: start;
  display: grid;
  min-height: calc(100vh - 220px);
  max-height: calc(100vh - 220px);
  overflow: hidden;
}

.console-body {
  display: grid;
  gap: 12px;
  align-content: start;
  overflow: auto;
  padding-right: 4px;
}

.collapsed-rail {
  display: flex;
  align-items: center;
  justify-content: center;
  writing-mode: vertical-rl;
  text-orientation: mixed;
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

@media (max-width: 1180px) {
  .editor-workspace.with-console,
  .editor-workspace.with-console.console-collapsed {
    grid-template-columns: 1fr;
    grid-template-areas:
      "toolbar"
      "stage"
      "console";
  }

  .docked-console {
    position: static;
    min-height: auto;
    max-height: none;
  }

  .collapsed-rail {
    writing-mode: horizontal-tb;
  }
}

@media (max-width: 760px) {
  .shell-toolbar {
    display: grid;
    gap: 12px;
  }
}
</style>
