<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ArrowLeft, DArrowLeft, DArrowRight } from "@element-plus/icons-vue";
import { ElMessageBox } from "element-plus";
import "element-plus/es/components/message-box/style/css";
import EditorShell from "../components/editor/EditorShell.vue";
import { apiFetch } from "../lib/api";

const route = useRoute();
const router = useRouter();

// 这里管理的是"编辑页级别"的页面状态，
// 和 EditorShell 内部的编辑器运行态分层开来，避免页面层直接承载桥接细节。
const isLoading = ref(true);
const errorMessage = ref("");
const currentDocument = ref(null);
const isSidebarOpen = ref(true);
const editorShellRef = ref(null);
const isLeaving = ref(false);

// 编辑页只认路由里的 documentId，把它视为唯一真相源。
// 这样"刷新当前页""回退高亮列表"都能围绕同一个 id 工作。
const currentDocumentId = computed(() => String(route.params.documentId ?? ""));

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

async function loadEditorPageData() {
  if (!currentDocument.value) {
    isLoading.value = true;
  }
  errorMessage.value = "";

  try {
    const detailResponse = await apiFetch(`/api/documents/${currentDocumentId.value}`);

    if (!detailResponse.ok) {
      throw new Error(await readErrorMessage(detailResponse, `文档详情加载失败，HTTP ${detailResponse.status}`));
    }

    currentDocument.value = await detailResponse.json();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "编辑页加载失败";
  } finally {
    isLoading.value = false;
    isLeaving.value = false;
  }
}

async function closeCurrentEditingSession() {
  // 页面层通过 ref 调用 EditorShell 暴露的方法，
  // 自己不直接拼 save/close API，避免离场逻辑分散在多个地方。
  if (!editorShellRef.value?.closeEditingSession) {
    return;
  }

  await editorShellRef.value.closeEditingSession();
}

async function requestOpenDocument(document) {
  if (!document || document.documentId === currentDocumentId.value || isLeaving.value) {
    return;
  }

  try {
    await ElMessageBox.confirm(
      `即将结束当前文档会话并打开"${document.title}"，是否继续？`,
      "切换文档",
      {
        confirmButtonText: "确认切换",
        cancelButtonText: "取消",
        type: "warning",
        appendTo: "body",
        "custom-class": "editor-leave-confirm"
      }
    );
  } catch {
    // 用户点击取消，留在当前文档
    return;
  }

  await runLeaveFlow(
    () => router.push({ name: "editor", params: { documentId: document.documentId } }),
    "切换文档失败"
  );
}

function toggleSidebar() {
  isSidebarOpen.value = !isSidebarOpen.value;
}

function formatTimestamp(value) {
  if (!value) {
    return "暂无";
  }

  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

async function runLeaveFlow(navigate, fallbackMessage) {
  if (isLeaving.value) {
    return;
  }

  // 离开当前编辑页时的统一出口：
  // 先结束当前编辑会话，再执行页面跳转。
  isLeaving.value = true;
  errorMessage.value = "";

  try {
    await closeCurrentEditingSession();
    await navigate();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : fallbackMessage;
    isLeaving.value = false;
  }
}

watch(
  () => currentDocumentId.value,
  () => {
    // 路由 documentId 变化时重新拉一整页上下文，让左栏和编辑区保持一致。
    loadEditorPageData();
  }
);

onBeforeRouteLeave(async () => {
  if (isLeaving.value) {
    return;
  }

  // 路由离开编辑页时先显式结束当前会话，
  // 避免列表页/预览页切换只依赖 iframe 内部的页面卸载兜底。
  isLeaving.value = true;
  errorMessage.value = "";

  try {
    await closeCurrentEditingSession();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "离开编辑页失败";
    isLeaving.value = false;
    return false;
  }
});

const saveStatus = computed(() => editorShellRef.value?.saveStatus);
const modeLabel = computed(() => editorShellRef.value?.modeLabel);

function loadSaveStatus() {
  editorShellRef.value?.loadSaveStatus?.();
}

function loadEditorConfig() {
  return editorShellRef.value?.loadEditorConfig?.();
}

function saveStatusTone(state) {
  return editorShellRef.value?.saveStatusTone?.(state) || "save-status-info";
}

async function handleFullRefresh() {
  try {
    await ElMessageBox.confirm(
      "此操作将放弃所有未同步到服务器的本地修改并原地重启编辑器 iframe。如果您确定已成功保存或遇到严重卡死，可继续操作。",
      "确认重置编辑器？",
      {
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
  } catch {
    return;
  }

  await loadEditorPageData();
  await loadEditorConfig();
}

onMounted(loadEditorPageData);
</script>

<template>
  <el-container class="editor-page-shell" direction="vertical">
    <el-alert key="error" v-if="errorMessage" :title="errorMessage" type="error" show-icon style="margin: 16px;">
      <el-button size="small" @click="loadEditorPageData" style="margin-top: 8px;">重新加载</el-button>
    </el-alert>

    <el-empty key="loading" v-else-if="isLoading" description="正在加载编辑页..." />

    <el-container key="content" v-else class="editor-layout">
      <div
        v-if="!isSidebarOpen"
        class="sidebar-strip-toggle"
        @click="toggleSidebar"
        title="展开侧边栏"
      >
        <el-icon><DArrowRight /></el-icon>
      </div>

      <el-aside
        v-show="isSidebarOpen"
        width="300px"
        class="editor-sidebar"
      >
        <div class="sidebar-header">
          <el-button link type="primary" @click="router.push('/')" class="back-button">
            <el-icon style="margin-right: 4px;"><ArrowLeft /></el-icon> 返回文档列表
          </el-button>
          <div class="sidebar-header-row">
            <div class="sidebar-header-meta">
              <p class="eyebrow">独立编辑工作台</p>
              <h1 class="sidebar-doc-title" :title="currentDocument?.title || currentDocumentId">
                {{ currentDocument?.title || currentDocumentId }}
              </h1>
            </div>
            <el-button type="primary" @click="toggleSidebar" title="收起侧边栏" style="padding: 8px;">
              <el-icon size="16"><DArrowLeft /></el-icon>
            </el-button>
          </div>
          <p class="muted-copy sidebar-notice" style="margin-bottom: 12px;">
            当前工作台已为 AI 对话侧栏预留选区与章节导航能力。离开页面前会显式结束编辑会话。
          </p>
          <div class="toolbar-actions">
            <el-button type="primary" size="small" :disabled="isLoading || isLeaving" @click="handleFullRefresh">
              重置并刷新编辑器
            </el-button>
          </div>
        </div>

        <el-divider style="margin: 16px 0" />

        <div class="sidebar-section">
          <p class="eyebrow">当前文档</p>
          <h2 style="margin: 4px 0 8px; font-size: 16px;">{{ currentDocument?.title || "未命名文档" }}</h2>
          <p class="muted-copy">最近保存：<code>{{ formatTimestamp(currentDocument?.lastSavedTime) }}</code></p>
          <p class="muted-copy">当前状态：<el-tag size="small">{{ currentDocument?.status || "未知" }}</el-tag></p>
        </div>

        <el-divider style="margin: 16px 0" />

        <div class="sidebar-section">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
            <p class="eyebrow" style="margin: 0;">实时保存状态</p>
            <el-button size="small" text @click="loadSaveStatus" title="刷新保存状态">刷新</el-button>
          </div>
          <div v-if="saveStatus" :class="['save-status-badge', saveStatusTone(saveStatus.status)]">
            <el-tag
              size="small"
              :type="saveStatus.status === 'saved' ? 'success' : saveStatus.status === 'editing' || saveStatus.status === 'callback-received' ? 'warning' : saveStatus.status === 'save-failed' ? 'danger' : 'info'"
            >{{ saveStatus.status || '未知' }}</el-tag>
            <span class="muted-copy" style="font-size: 12px; margin-left: 6px;" v-if="saveStatus.lastSavedTime">
              {{ formatTimestamp(saveStatus.lastSavedTime) }}
            </span>
          </div>
          <div v-if="saveStatus?.events?.length" style="margin-top: 8px;">
            <ul class="save-status-events">
              <li v-for="(ev, i) in (saveStatus.events || []).slice(0, 3)" :key="i">
                <strong>{{ ev.type || ev.event }}</strong>
                <time v-if="ev.time || ev.timestamp">{{ formatTimestamp(ev.time || ev.timestamp) }}</time>
              </li>
            </ul>
          </div>
          <p v-else-if="!saveStatus" class="muted-copy" style="font-size: 12px; margin-top: 4px;">等待编辑器就绪...</p>
        </div>

        <el-divider style="margin: 16px 0" />

        <div class="sidebar-section">
          <p class="muted-copy">当前模式：<el-tag size="small">{{ modeLabel }}</el-tag></p>
        </div>
      </el-aside>

      <el-main class="editor-stage">
        <EditorShell
          ref="editorShellRef"
          :document-id="currentDocumentId"
          :document-title="currentDocument?.title || currentDocumentId"
        />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.editor-page-shell {
  min-height: 100vh;
  background-color: var(--el-bg-color-page);
}

.editor-layout {
  height: 100vh;
  min-height: 0;
}

.editor-stage {
  padding: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.editor-sidebar {
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color);
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.sidebar-strip-toggle {
  height: 48px;
  width: 24px;
  background: var(--el-color-primary);
  border: 1px solid var(--el-color-primary);
  border-left: none;
  border-radius: 0 4px 4px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 100;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  margin-top: auto;
  margin-bottom: auto;
  color: #fff;
  transition: all 0.2s ease;
}

.sidebar-strip-toggle:hover {
  background-color: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
}

.back-button {
  margin-bottom: 12px;
  padding: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.back-button:hover {
  color: var(--el-color-primary);
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0;
}

.muted-copy {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
  margin: 4px 0;
}

.sidebar-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.sidebar-doc-title {
  margin: 4px 0 0;
  font-size: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.switch-list {
  display: grid;
  gap: 8px;
}

.switch-item {
  cursor: pointer;
}

.switch-item.active {
  border-color: var(--el-color-primary-light-5);
  background-color: var(--el-color-primary-light-9);
}

.switch-item.disabled {
  opacity: 0.6;
  pointer-events: none;
}

.switch-title {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.switch-meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
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

.save-status-badge {
  display: flex;
  align-items: center;
  padding: 6px 0;
}
</style>
