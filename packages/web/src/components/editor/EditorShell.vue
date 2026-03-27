<script setup>
import { onBeforeUnmount, ref, watch } from "vue";
import { DocumentEditor } from "@onlyoffice/document-editor-vue";

const props = defineProps({
  documentId: {
    type: String,
    required: true
  },
  documentTitle: {
    type: String,
    default: ""
  }
});

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

// 这个组件只负责“单文档编辑运行态”：
// - 拉取 editor-config；
// - 挂载 ONLYOFFICE 编辑器；
// - 轮询保存状态；
// - 承接右侧控制台动作。
// 文档切换、返回列表等页面级能力由外层编辑页负责。
const readonly = ref(false);
const imageUrl = ref("https://upload.wikimedia.org/wikipedia/commons/6/63/Wikipedia-logo.png");
const isPanelOpen = ref(false);
const isLoading = ref(true);
const isInsertingImage = ref(false);
const errorMessage = ref("");
const editorPayload = ref(null);
const editorKey = ref(0);
const saveStatus = ref(null);
let saveStatusTimer = null;

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

async function loadEditorConfig() {
  // editor-config 是 ONLYOFFICE 宿主最核心的运行时配置。
  // 每当文档切换、只读模式切换或用户主动刷新时，都需要重新向后端获取一次。
  isLoading.value = true;
  errorMessage.value = "";

  try {
    const params = new URLSearchParams({
      readonly: String(readonly.value)
    });
    const response = await fetch(
      `${apiBaseUrl}/api/documents/${props.documentId}/editor-config?${params.toString()}`
    );
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `配置请求失败，HTTP ${response.status}`));
    }

    editorPayload.value = await response.json();
    // 重新拿到配置后顺手刷新一次保存状态，保证控制台里的信息与当前文档同步。
    await loadSaveStatus();
    // ONLYOFFICE Vue 包裹组件对深层配置变化不总是完全响应，
    // 因此这里通过递增 key 强制重建编辑器实例，确保新配置生效。
    editorKey.value += 1;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "未知错误";
  } finally {
    isLoading.value = false;
  }
}

async function loadSaveStatus() {
  try {
    const response = await fetch(`${apiBaseUrl}/api/documents/${props.documentId}/save-status`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `状态请求失败，HTTP ${response.status}`));
    }
    saveStatus.value = await response.json();
  } catch (error) {
    // 保存状态失败不阻塞主编辑器渲染，只在控制台排障即可。
    console.error("加载保存状态失败", error);
  }
}

async function toggleReadonly() {
  // 只读/可编辑本质上会改变后端生成的 editor-config，因此不能只改本地标记。
  readonly.value = !readonly.value;
  await loadEditorConfig();
}

function togglePanel() {
  isPanelOpen.value = !isPanelOpen.value;
}

function closePanel() {
  isPanelOpen.value = false;
}

function getDocEditorInstance() {
  // ONLYOFFICE 组件实例通过全局对象暴露，这里集中封装读取逻辑，
  // 避免业务代码到处直接访问 window.DocEditor。
  return window.DocEditor?.instances?.docEditor;
}

async function insertRemoteImage() {
  if (readonly.value) {
    errorMessage.value = "只读模式下不能插入图片。";
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
    // 先让后端完成图片代理、安全校验和插图 payload 生成，
    // 前端只负责把 payload 交给编辑器，不自己拼接插图协议。
    const response = await fetch(`${apiBaseUrl}/api/documents/${props.documentId}/images/insert`, {
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
  // 编辑器真正 ready 后再启动状态轮询，避免在组件未挂载完成时提前打接口。
  console.log("ONLYOFFICE 文档已加载完成");
  startSaveStatusPolling();
}

function handleLoadComponentError(errorCode, errorDescription) {
  // ONLYOFFICE 静态资源、脚本或运行时协议异常时，这里统一转换成页面可见错误。
  errorMessage.value = `ONLYOFFICE 组件加载失败（${errorCode}）：${errorDescription}`;
}

function startSaveStatusPolling() {
  // 轮询前先清理旧定时器，避免重复进入编辑页或切文档时产生并发轮询。
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
  // 保存状态颜色统一在这里映射，模板只负责消费语义化 class。
  return {
    "is-idle": state === "idle",
    "is-progress": state === "callback-received",
    "is-success": state === "saved",
    "is-error": state === "save-failed"
  };
}

watch(
  () => props.documentId,
  async () => {
    // 切换文档时必须完整重置运行态：
    // - 停掉旧轮询；
    // - 回到可编辑默认值；
    // - 清空上一个文档的保存状态；
    // - 重新拉取当前文档配置。
    stopSaveStatusPolling();
    readonly.value = false;
    saveStatus.value = null;
    await loadEditorConfig();
  },
  { immediate: true }
);

// 组件卸载时要及时停掉轮询，避免离开编辑页后仍持续请求 save-status。
onBeforeUnmount(stopSaveStatusPolling);
</script>

<template>
  <section class="editor-workspace">
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

    <button class="panel-toggle" type="button" @click="togglePanel">
      {{ isPanelOpen ? "收起控制台" : "打开控制台" }}
    </button>

    <transition name="panel-fade">
      <button
        v-if="isPanelOpen"
        class="panel-backdrop"
        type="button"
        aria-label="关闭控制台"
        @click="closePanel"
      />
    </transition>

    <aside class="side-panel" :class="{ open: isPanelOpen }" aria-label="编辑器控制台">
      <div class="side-panel-header">
        <div class="hero-copy">
          <p class="eyebrow">独立编辑页</p>
          <h2>{{ props.documentTitle || props.documentId }}</h2>
          <p class="summary">
            编辑页只负责当前文档的运行态能力，切换文档、返回列表和创建入口都交给工作台首页与外层页面壳层处理。
          </p>
        </div>
        <button class="panel-close" type="button" @click="closePanel">
          关闭
        </button>
      </div>

      <div class="drawer-actions">
        <section class="panel-section">
          <p class="panel-section-title">当前文档</p>
          <p class="panel-document-title">{{ props.documentTitle || "未命名文档" }}</p>
          <p class="panel-document-meta">documentId: <code>{{ props.documentId }}</code></p>
          <p class="panel-document-meta">当前模式：<code>{{ readonly ? "只读" : "可编辑" }}</code></p>
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
            :disabled="isLoading || isInsertingImage || readonly"
            @click="insertRemoteImage"
          >
            {{ isInsertingImage ? "插入中..." : "在光标处插入网络图片" }}
          </button>
          <button class="ghost-button" type="button" :disabled="isLoading" @click="toggleReadonly">
            {{ readonly ? "切换为可编辑" : "切换为只读" }}
          </button>
          <button class="ghost-button secondary" type="button" :disabled="isLoading" @click="loadEditorConfig">
            重新加载配置
          </button>
        </section>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.editor-workspace {
  min-height: 0;
  height: 100%;
}

.side-panel-header {
  display: grid;
  gap: 14px;
}

.side-panel-header h2 {
  margin: 8px 0 0;
  font-size: clamp(26px, 3vw, 38px);
}

.drawer-actions {
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
  color: var(--accent);
}

.save-status-events span,
.save-status-events time {
  font-size: 12px;
  color: var(--muted-strong);
}
</style>
