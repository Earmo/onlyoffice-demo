<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import EditorShell from "../components/editor/EditorShell.vue";
import { apiFetch } from "../lib/api";

const route = useRoute();
const router = useRouter();

const isLoading = ref(true);
const errorMessage = ref("");
const currentDocument = ref(null);
const documents = ref([]);
const isSidebarOpen = ref(true);
const editorShellRef = ref(null);

// 编辑页只认路由里的 documentId，把它视为唯一真相源。
// 这样“切换文档”“刷新当前页”“回退高亮列表”都能围绕同一个 id 工作。
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
  }
}

async function closeCurrentEditingSession() {
  if (!editorShellRef.value?.closeEditingSession) {
    return;
  }

  await editorShellRef.value.closeEditingSession();
}

async function goBackToLibrary() {
  try {
    await closeCurrentEditingSession();
    await router.push({ path: "/", query: { highlight: currentDocumentId.value } });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "结束当前编辑会话失败";
  }
}

async function requestOpenDocument(document) {
  if (!document || document.documentId === currentDocumentId.value) {
    return;
  }

  const confirmed = window.confirm(`即将结束当前文档会话并打开“${document.title}”，是否继续？`);
  if (!confirmed) {
    return;
  }

  try {
    await closeCurrentEditingSession();
    await router.push({ name: "editor", params: { documentId: document.documentId } });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "切换文档失败";
  }
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

watch(
  () => currentDocumentId.value,
  () => {
    loadEditorPageData();
  }
);

onMounted(loadEditorPageData);
</script>

<template>
  <main class="page-shell editor-page-shell">
    <section v-if="errorMessage" class="state-card error inline-state">
      <p>{{ errorMessage }}</p>
      <button class="ghost-button secondary compact" type="button" @click="loadEditorPageData">
        重新加载
      </button>
    </section>

    <section v-else-if="isLoading" class="state-card inline-state">
      <p>正在加载编辑页...</p>
    </section>

    <section v-else class="editor-layout">
      <button
        v-if="!isSidebarOpen"
        class="sidebar-strip-toggle"
        type="button"
        @click="toggleSidebar"
        title="展开侧边栏"
      >◄</button>

      <aside
        class="surface-panel editor-sidebar"
        :style="{ width: isSidebarOpen ? '300px' : '0', opacity: isSidebarOpen ? '1' : '0', padding: isSidebarOpen ? undefined : '0', overflow: isSidebarOpen ? undefined : 'hidden' }"
      >
        <section class="sidebar-section sidebar-header">
          <div class="sidebar-header-row">
            <div class="sidebar-header-meta">
              <p class="eyebrow">独立编辑工作台</p>
              <h1 class="sidebar-doc-title" :title="currentDocument?.title || currentDocumentId">
                {{ currentDocument?.title || currentDocumentId }}
              </h1>
            </div>
            <button class="ghost-button compact" type="button" @click="toggleSidebar" title="收起侧边栏">▶</button>
          </div>
          <p class="muted-copy sidebar-notice">
            Phase 9 起固定为可编辑工作台。离开页面前会显式结束编辑会话。
          </p>
          <div class="toolbar-actions">
            <button class="ghost-button secondary compact" type="button" @click="goBackToLibrary">返回文档列表</button>
            <button class="ghost-button compact" type="button" :disabled="isLoading" @click="loadEditorPageData">刷新文档上下文</button>
          </div>
        </section>

        <section class="sidebar-section">
          <p class="eyebrow">当前文档</p>
          <h2>{{ currentDocument?.title || "未命名文档" }}</h2>
          <p class="muted-copy">最近保存：<code>{{ formatTimestamp(currentDocument?.lastSavedTime) }}</code></p>
          <p class="muted-copy">当前状态：<code>{{ currentDocument?.status || "未知" }}</code></p>
        </section>

        <section class="sidebar-section">
          <div class="sidebar-heading">
            <div>
              <p class="eyebrow">切换文档</p>
              <h3>最近文档</h3>
            </div>
          </div>

          <div class="switch-list">
            <button
              v-for="document in documents"
              :key="document.documentId"
              class="switch-item"
              :class="{ active: document.documentId === currentDocumentId }"
              :title="document.title"
              type="button"
              @click="requestOpenDocument(document)"
            >
              <span class="switch-title">{{ document.title }}</span>
              <span class="switch-meta">{{ formatTimestamp(document.lastSavedTime) }}</span>
            </button>
          </div>
        </section>
      </aside>

      <section class="editor-stage">
        <EditorShell
          ref="editorShellRef"
          :document-id="currentDocumentId"
          :document-title="currentDocument?.title || currentDocumentId"
        />
      </section>
    </section>
  </main>
</template>

<style scoped>
.editor-page-shell {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  padding-bottom: 0;
  gap: 0;
}

.editor-layout {
  flex: 1;
  display: flex;
  align-items: stretch;
  gap: 16px;
  padding: 18px 0 18px;
  min-height: 0;
}

.editor-stage {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.editor-sidebar {
  display: grid;
  gap: 16px;
  align-content: start;
  width: 300px;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 220ms ease, opacity 220ms ease;
}

.sidebar-section {
  display: grid;
  gap: 12px;
}

.sidebar-section h2,
.sidebar-section h3 {
  margin: 4px 0 0;
}

.sidebar-strip-toggle {
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

.sidebar-header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.sidebar-doc-title {
  margin: 8px 0 0;
  font-size: clamp(18px, 2vw, 26px);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.sidebar-notice {
  font-size: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.switch-list {
  display: grid;
  gap: 10px;
}

.switch-item {
  display: grid;
  gap: 6px;
  text-align: left;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--surface-border);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
}

.switch-item.active {
  border-color: rgba(139, 94, 52, 0.26);
  background: rgba(255, 248, 240, 0.88);
}

.switch-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.switch-meta {
  color: var(--muted-soft);
  font-size: 13px;
}

@media (max-width: 980px) {
  .editor-layout {
    flex-direction: column;
  }

  .editor-sidebar {
    width: 100% !important;
  }
}
</style>
