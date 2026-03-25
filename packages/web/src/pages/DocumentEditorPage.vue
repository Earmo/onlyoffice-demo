<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import EditorShell from "../components/editor/EditorShell.vue";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

const route = useRoute();
const router = useRouter();

const isLoading = ref(true);
const errorMessage = ref("");
const currentDocument = ref(null);
const documents = ref([]);

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
  isLoading.value = true;
  errorMessage.value = "";

  try {
    const [detailResponse, listResponse] = await Promise.all([
      fetch(`${apiBaseUrl}/api/documents/${currentDocumentId.value}`),
      fetch(`${apiBaseUrl}/api/documents`)
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

function goBackToLibrary() {
  router.push({ path: "/", query: { highlight: currentDocumentId.value } });
}

async function requestOpenDocument(document) {
  if (!document || document.documentId === currentDocumentId.value) {
    return;
  }

  const confirmed = window.confirm(`即将离开当前文档并打开“${document.title}”，是否继续？`);
  if (!confirmed) {
    return;
  }

  await router.push({ name: "editor", params: { documentId: document.documentId } });
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
    <section class="surface-panel editor-page-header">
      <div class="header-copy">
        <p class="eyebrow">独立编辑工作台</p>
        <h1>{{ currentDocument?.title || currentDocumentId }}</h1>
        <p class="muted-copy">
          编辑页和列表页已经分开，返回文档工作台或切换到其他文档时都会走明确入口，不再依赖浏览器隐式回退。
        </p>
      </div>
      <div class="header-actions">
        <button class="ghost-button secondary" type="button" @click="goBackToLibrary">
          返回文档列表
        </button>
        <button class="ghost-button compact" type="button" :disabled="isLoading" @click="loadEditorPageData">
          刷新文档上下文
        </button>
      </div>
    </section>

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

.editor-page-header {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-end;
}

.header-copy h1 {
  margin: 8px 0 10px;
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.editor-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 320px minmax(0, 1fr);
  min-height: calc(100vh - 220px);
}

.editor-sidebar {
  display: grid;
  gap: 16px;
  align-content: start;
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

.editor-stage {
  min-width: 0;
}

@media (max-width: 980px) {
  .editor-page-header {
    display: grid;
    gap: 12px;
  }

  .editor-layout {
    grid-template-columns: 1fr;
  }
}
</style>
