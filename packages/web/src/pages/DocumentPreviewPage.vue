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

const currentDocumentId = computed(() => String(route.params.documentId ?? ""));

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

async function loadPreviewPageData() {
  isLoading.value = true;
  errorMessage.value = "";

  try {
    const response = await apiFetch(`/api/documents/${currentDocumentId.value}`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `文档详情加载失败，HTTP ${response.status}`));
    }
    currentDocument.value = await response.json();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "预览页加载失败";
  } finally {
    isLoading.value = false;
  }
}

function goBackToLibrary() {
  router.push({ path: "/", query: { highlight: currentDocumentId.value } });
}

function goToEditor() {
  router.push({ name: "editor", params: { documentId: currentDocumentId.value } });
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
    loadPreviewPageData();
  }
);

onMounted(loadPreviewPageData);
</script>

<template>
  <main class="page-shell preview-page-shell">
    <section class="surface-panel preview-header">
      <div class="preview-copy">
        <p class="eyebrow">只读预览</p>
        <h1>{{ currentDocument?.title || currentDocumentId }}</h1>
        <p class="muted-copy">
          这里用于安全查看文档内容，不建立活跃编辑会话；若需要修改，请进入独立编辑工作台。
        </p>
      </div>
      <div class="preview-actions">
        <button class="ghost-button secondary" type="button" @click="goBackToLibrary">
          返回文档列表
        </button>
        <button class="ghost-button compact" type="button" :disabled="isLoading" @click="goToEditor">
          编辑文档
        </button>
      </div>
    </section>

    <section v-if="errorMessage" class="state-card error inline-state">
      <p>{{ errorMessage }}</p>
      <button class="ghost-button secondary compact" type="button" @click="loadPreviewPageData">
        重新加载
      </button>
    </section>

    <section v-else-if="isLoading" class="state-card inline-state">
      <p>正在加载预览页...</p>
    </section>

    <section v-else class="preview-layout">
      <section class="preview-stage">
        <EditorShell
          :document-id="currentDocumentId"
          :document-title="currentDocument?.title || currentDocumentId"
          :readonly="true"
          :show-console="false"
        />
      </section>

      <aside class="surface-panel preview-sidebar">
        <section class="preview-meta-section">
          <p class="eyebrow">文档信息</p>
          <h2>{{ currentDocument?.title || "未命名文档" }}</h2>
          <p class="muted-copy">最近保存：<code>{{ formatTimestamp(currentDocument?.lastSavedTime) }}</code></p>
          <p class="muted-copy">当前状态：<code>{{ currentDocument?.status || "未知" }}</code></p>
          <p class="muted-copy">documentId：<code>{{ currentDocumentId }}</code></p>
        </section>

        <section class="preview-meta-section">
          <p class="eyebrow">下一步</p>
          <p class="muted-copy">
            预览页只负责查看内容，不会显示编辑控制台，也不会建立“编辑中”会话状态。
          </p>
          <button class="ghost-button compact" type="button" @click="goToEditor">
            进入编辑工作台
          </button>
        </section>
      </aside>
    </section>
  </main>
</template>

<style scoped>
.preview-page-shell {
  display: grid;
  gap: 18px;
  padding-bottom: 28px;
}

.preview-header {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-end;
}

.preview-copy h1 {
  margin: 8px 0 10px;
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1;
}

.preview-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.preview-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) 320px;
  min-height: calc(100vh - 220px);
}

.preview-stage {
  min-width: 0;
  height: 100%;
}

.preview-sidebar {
  display: grid;
  gap: 16px;
  align-content: start;
  position: sticky;
  top: 20px;
  height: fit-content;
}

.preview-meta-section {
  display: grid;
  gap: 12px;
}

.preview-meta-section h2 {
  margin: 4px 0 0;
}

@media (max-width: 980px) {
  .preview-header {
    display: grid;
    gap: 12px;
  }

  .preview-layout {
    grid-template-columns: 1fr;
  }

  .preview-sidebar {
    position: static;
  }
}
</style>
