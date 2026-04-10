<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
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
const documents = ref([]);
const isSidebarOpen = ref(true);
const editorShellRef = ref(null);
const isLeaving = ref(false);

// 编辑页只认路由里的 documentId，把它视为唯一真相源。
// 这样"切换文档""刷新当前页""回退高亮列表"都能围绕同一个 id 工作。
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
  // 编辑页需要同时拿到：
  // 1. 当前文档详情，用于顶部提示区和左侧固定栏展示；
  // 2. 最近文档列表，用于左侧固定栏切换入口。
  // 这里并行请求，减少进入编辑页的等待时间。
  isLoading.value = true;
  errorMessage.value = "";

  try {
    const [detailResponse, listResponse] = await Promise.all([
      apiFetch(`/api/documents/${currentDocumentId.value}`),
      apiFetch("/api/documents")
    ]);

    if (!detailResponse.ok) {
      throw new Error(await readErrorMessage(detailResponse, `文档详情加载失败，HTTP ${detailResponse.status}`));
    }
    if (!listResponse.ok) {
      throw new Error(await readErrorMessage(listResponse, `文档列表加载失败，HTTP ${listResponse.status}`));
    }

    currentDocument.value = await detailResponse.json();
    const listPayload = await listResponse.json();
    documents.value = listPayload.documents ?? [];
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

onMounted(loadEditorPageData);
</script>

<template>
  <el-container class="editor-page-shell" direction="vertical">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon style="margin: 16px;">
      <el-button size="small" @click="loadEditorPageData" style="margin-top: 8px;">重新加载</el-button>
    </el-alert>

    <el-empty v-else-if="isLoading" description="正在加载编辑页..." />

    <el-container v-else class="editor-layout">
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
            <el-button type="primary" size="small" :disabled="isLoading || isLeaving" @click="loadEditorPageData">
              刷新文档上下文
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
          <div class="sidebar-heading" style="margin-bottom: 8px;">
            <p class="eyebrow">切换文档</p>
            <h3 style="margin: 4px 0; font-size: 16px;">最近文档</h3>
          </div>

          <div class="switch-list">
            <el-card
              v-for="document in documents"
              :key="document.documentId"
              shadow="hover"
              class="switch-item"
              :class="{ active: document.documentId === currentDocumentId, disabled: isLeaving }"
              @click="requestOpenDocument(document)"
              body-style="padding: 12px;"
            >
              <div class="switch-title" :title="document.title">{{ document.title }}</div>
              <div class="switch-meta">{{ formatTimestamp(document.lastSavedTime) }}</div>
            </el-card>
          </div>
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
</style>
