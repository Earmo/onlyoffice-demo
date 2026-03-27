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
const isTopNoticeCollapsed = ref(false);
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

function toggleTopNotice() {
  isTopNoticeCollapsed.value = !isTopNoticeCollapsed.value;
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
    <template v-if="!isTopNoticeCollapsed">
      <section class="surface-panel editor-page-toolbar">
        <div class="toolbar-copy">
          <p class="eyebrow">独立编辑工作台</p>
          <h1>{{ currentDocument?.title || currentDocumentId }}</h1>
        </div>
        <div class="toolbar-actions">
          <button class="ghost-button secondary" type="button" @click="goBackToLibrary">
            返回文档列表
          </button>
          <button class="ghost-button compact" type="button" :disabled="isLoading" @click="loadEditorPageData">
            刷新文档上下文
          </button>
          <button class="ghost-button compact" type="button" @click="toggleTopNotice">
            收起提示
          </button>
        </div>
      </section>

      <section class="surface-panel top-notice-card">
        <p class="eyebrow">编辑提示</p>
        <p class="muted-copy">
          编辑页从 Phase 9 起固定为可编辑工作台。返回列表或切换文档时会显式结束当前编辑会话，避免列表中的“编辑中”状态滞留。
        </p>
      </section>
    </template>

    <button v-else class="ghost-button compact editor-topbar-reveal" type="button" @click="toggleTopNotice">
      展开顶部信息
    </button>

    <section v-if="errorMessage" class="state-card error inline-state">
      <p>{{ errorMessage }}</p>
      <button class="ghost-button secondary compact" type="button" @click="loadEditorPageData">
        重新加载
      </button>
    </section>

    <section v-else-if="isLoading" class="state-card inline-state">
      <p>正在加载编辑页...</p>
    </section>

    <section v-else class="editor-layout" :class="{ compact: isTopNoticeCollapsed }">
      <aside class="surface-panel editor-sidebar">
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
  display: grid;
  gap: 18px;
  padding-bottom: 28px;
}

.editor-page-toolbar {
  position: sticky;
  top: 0;
  z-index: 8;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-end;
}

.toolbar-copy h1 {
  margin: 8px 0 0;
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.top-notice-card {
  display: grid;
  gap: 10px;
}

.editor-topbar-reveal {
  position: sticky;
  top: 12px;
  z-index: 8;
  justify-self: end;
}

.editor-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  min-height: calc(100vh - 250px);
  align-items: start;
}

.editor-layout.compact {
  min-height: calc(100vh - 180px);
}

.editor-stage {
  min-width: 0;
  min-height: 0;
}

.editor-sidebar {
  display: grid;
  gap: 16px;
  align-content: start;
  position: sticky;
  top: 96px;
  height: fit-content;
}

.sidebar-section {
  display: grid;
  gap: 12px;
}

.sidebar-section h2,
.sidebar-section h3 {
  margin: 4px 0 0;
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
}

.switch-meta {
  color: var(--muted-soft);
  font-size: 13px;
}

@media (max-width: 980px) {
  .editor-page-toolbar {
    display: grid;
    gap: 12px;
    position: static;
  }

  .editor-topbar-reveal {
    position: static;
    justify-self: start;
  }

  .editor-layout {
    grid-template-columns: 1fr;
  }

  .editor-sidebar {
    position: static;
  }
}
</style>
