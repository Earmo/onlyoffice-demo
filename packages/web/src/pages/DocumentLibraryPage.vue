<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import DocumentCreateActions from "../components/library/DocumentCreateActions.vue";
import DocumentList from "../components/library/DocumentList.vue";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

const route = useRoute();
const router = useRouter();

const documents = ref([]);
const tenantId = ref("");
const actorUser = ref("");
const actorName = ref("");
const isLoading = ref(true);
const errorMessage = ref("");
const successMessage = ref("");
const highlightedDocumentId = ref("");
const remoteDocumentUrl = ref("");

const isCreating = ref(false);
const isUploading = ref(false);
const isImporting = ref(false);

const searchQuery = ref("");
const statusFilter = ref("all");
const documentTypeFilter = ref("all");
const sourceSystemFilter = ref("all");
const storageFilter = ref("all");
const sortDirection = ref("desc");

const recentDocuments = computed(() => documents.value.slice(0, 3));

const hasActiveQuery = computed(() => {
  return Boolean(
    searchQuery.value
      || statusFilter.value !== "all"
      || documentTypeFilter.value !== "all"
      || sourceSystemFilter.value !== "all"
      || storageFilter.value !== "all"
      || sortDirection.value !== "desc"
  );
});

const statusOptions = computed(() => uniqueOptions(documents.value.map(document => document.status)));
const documentTypeOptions = computed(() => uniqueOptions(documents.value.map(document => document.documentType)));
const sourceSystemOptions = computed(() => uniqueOptions(documents.value.map(document => document.sourceSystem)));

function uniqueOptions(values) {
  return [...new Set(values.filter(Boolean))];
}

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

function buildListParams() {
  const params = new URLSearchParams();
  if (searchQuery.value) {
    params.set("query", searchQuery.value);
  }
  if (statusFilter.value !== "all") {
    params.set("status", statusFilter.value);
  }
  if (documentTypeFilter.value !== "all") {
    params.set("documentType", documentTypeFilter.value);
  }
  if (sourceSystemFilter.value !== "all") {
    params.set("sourceSystem", sourceSystemFilter.value);
  }
  if (storageFilter.value !== "all") {
    params.set("storage", storageFilter.value);
  }
  if (sortDirection.value !== "desc") {
    params.set("sortDirection", sortDirection.value);
  }
  return params;
}

async function loadDocuments() {
  isLoading.value = true;
  errorMessage.value = "";

  try {
    const params = buildListParams();
    const suffix = params.toString() ? `?${params.toString()}` : "";
    const response = await fetch(`${apiBaseUrl}/api/documents${suffix}`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `文档列表加载失败，HTTP ${response.status}`));
    }

    const payload = await response.json();
    documents.value = payload.documents ?? [];
    tenantId.value = payload.tenantId ?? "";
    actorUser.value = payload.actorUser ?? "";
    actorName.value = payload.actorName ?? "";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "文档列表加载失败";
  } finally {
    isLoading.value = false;
  }
}

function syncHighlightFromRoute() {
  const highlight = route.query.highlight;
  highlightedDocumentId.value = typeof highlight === "string" ? highlight : "";
}

async function revealDocument(documentSummary, message) {
  // 创建成功后的文档必须对用户可见，因此主动回到“全部文档”视图，避免被旧筛选条件隐藏。
  searchQuery.value = "";
  statusFilter.value = "all";
  documentTypeFilter.value = "all";
  sourceSystemFilter.value = "all";
  storageFilter.value = "all";
  sortDirection.value = "desc";
  highlightedDocumentId.value = documentSummary.documentId;
  successMessage.value = message;
  await router.replace({ path: "/", query: { highlight: documentSummary.documentId } });
  await loadDocuments();
}

async function createDocument() {
  const title = window.prompt("请输入文档标题（可留空使用默认标题）", "未命名文档.docx");
  if (title === null) {
    return;
  }

  isCreating.value = true;
  successMessage.value = "";
  errorMessage.value = "";

  try {
    const response = await fetch(`${apiBaseUrl}/api/documents`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        title: title || null
      })
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `创建文档失败，HTTP ${response.status}`));
    }

    const documentSummary = await response.json();
    await revealDocument(documentSummary, `已创建 ${documentSummary.title}，结果已回到工作台列表。`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "创建文档失败";
  } finally {
    isCreating.value = false;
  }
}

async function handleFileSelected(file) {
  if (!file) {
    return;
  }

  isUploading.value = true;
  successMessage.value = "";
  errorMessage.value = "";

  try {
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${apiBaseUrl}/api/documents/upload`, {
      method: "POST",
      body: formData
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `上传文档失败，HTTP ${response.status}`));
    }

    const documentSummary = await response.json();
    await revealDocument(documentSummary, `已上传 ${documentSummary.title}，结果已回到工作台列表。`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "上传文档失败";
  } finally {
    isUploading.value = false;
  }
}

async function importRemoteDocument() {
  isImporting.value = true;
  successMessage.value = "";
  errorMessage.value = "";

  try {
    const response = await fetch(`${apiBaseUrl}/api/documents/import-remote`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        sourceUrl: remoteDocumentUrl.value
      })
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `导入网络文档失败，HTTP ${response.status}`));
    }

    const documentSummary = await response.json();
    remoteDocumentUrl.value = "";
    await revealDocument(documentSummary, `已导入 ${documentSummary.title}，结果已回到工作台列表。`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "导入网络文档失败";
  } finally {
    isImporting.value = false;
  }
}

function openDocument(document) {
  router.push({ name: "editor", params: { documentId: document.documentId } });
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

async function applyFilters() {
  successMessage.value = "";
  await loadDocuments();
}

async function resetFilters() {
  searchQuery.value = "";
  statusFilter.value = "all";
  documentTypeFilter.value = "all";
  sourceSystemFilter.value = "all";
  storageFilter.value = "all";
  sortDirection.value = "desc";
  successMessage.value = "";
  await router.replace({ path: "/", query: {} });
  await loadDocuments();
}

watch(
  () => route.query.highlight,
  () => {
    syncHighlightFromRoute();
  },
  { immediate: true }
);

onMounted(loadDocuments);
</script>

<template>
  <main class="page-shell library-shell">
    <section class="hero-grid">
      <DocumentCreateActions
        :is-creating="isCreating"
        :is-uploading="isUploading"
        :is-importing="isImporting"
        :remote-document-url="remoteDocumentUrl"
        @create="createDocument"
        @file-selected="handleFileSelected"
        @import-remote="importRemoteDocument"
        @update:remoteDocumentUrl="remoteDocumentUrl = $event"
      />

      <div class="library-side-stack">
        <section class="surface-panel context-card">
          <p class="eyebrow">当前上下文</p>
          <h2>文档工作台</h2>
          <p class="muted-copy">
            当前租户 <code>{{ tenantId || "未解析" }}</code>，当前用户
            <code>{{ actorName || actorUser || "未解析" }}</code>。
          </p>
        </section>

        <section class="surface-panel recent-card">
          <div class="section-header compact">
            <div>
              <p class="eyebrow">最近文档</p>
              <h2>继续上次工作</h2>
            </div>
          </div>

          <div v-if="recentDocuments.length" class="recent-grid">
            <button
              v-for="document in recentDocuments"
              :key="document.documentId"
              class="recent-item"
              type="button"
              @click="openDocument(document)"
            >
              <span class="recent-title">{{ document.title }}</span>
              <span class="recent-meta">{{ formatTimestamp(document.lastSavedTime) }}</span>
            </button>
          </div>
          <p v-else class="muted-copy">还没有最近文档，先创建或上传第一份内容。</p>
        </section>
      </div>
    </section>

    <section class="surface-panel toolbar-panel">
      <form class="toolbar-grid" @submit.prevent="applyFilters">
        <label class="field-grid wide">
          <span>搜索文档</span>
          <input
            v-model="searchQuery"
            class="surface-input"
            type="search"
            placeholder="按标题、documentId 或外部文档 ID 搜索"
          />
        </label>

        <label class="field-grid">
          <span>状态</span>
          <select v-model="statusFilter" class="surface-select">
            <option value="all">全部状态</option>
            <option v-for="status in statusOptions" :key="status" :value="status">
              {{ status }}
            </option>
          </select>
        </label>

        <label class="field-grid">
          <span>文档类型</span>
          <select v-model="documentTypeFilter" class="surface-select">
            <option value="all">全部类型</option>
            <option v-for="documentType in documentTypeOptions" :key="documentType" :value="documentType">
              {{ documentType }}
            </option>
          </select>
        </label>

        <label class="field-grid">
          <span>来源系统</span>
          <select v-model="sourceSystemFilter" class="surface-select">
            <option value="all">全部来源</option>
            <option v-for="sourceSystem in sourceSystemOptions" :key="sourceSystem" :value="sourceSystem">
              {{ sourceSystem }}
            </option>
          </select>
        </label>

        <label class="field-grid">
          <span>对象可用性</span>
          <select v-model="storageFilter" class="surface-select">
            <option value="all">全部</option>
            <option value="available">仅可用</option>
            <option value="unavailable">仅异常</option>
          </select>
        </label>

        <label class="field-grid">
          <span>排序</span>
          <select v-model="sortDirection" class="surface-select">
            <option value="desc">最近优先</option>
            <option value="asc">较早优先</option>
          </select>
        </label>

        <div class="toolbar-actions">
          <button class="ghost-button compact" type="submit" :disabled="isLoading">
            搜索与筛选
          </button>
          <button class="ghost-button secondary compact" type="button" :disabled="isLoading" @click="resetFilters">
            重置
          </button>
        </div>
      </form>
    </section>

    <section v-if="successMessage" class="state-card success-banner">
      <p>{{ successMessage }}</p>
    </section>

    <section v-if="errorMessage" class="state-card error inline-state">
      <p>{{ errorMessage }}</p>
      <button class="ghost-button secondary compact" type="button" @click="loadDocuments">
        重新加载
      </button>
    </section>

    <section v-else-if="isLoading" class="state-card inline-state">
      <p>正在加载文档工作台...</p>
    </section>

    <section
      v-else-if="!documents.length && hasActiveQuery"
      class="state-card inline-state"
    >
      <p>当前搜索或筛选条件下没有找到文档。</p>
      <button class="ghost-button secondary compact" type="button" @click="resetFilters">
        清空筛选
      </button>
    </section>

    <section v-else-if="!documents.length" class="state-card inline-state">
      <p>当前租户下还没有任何文档。</p>
      <p class="hint">从上方主操作区新建或上传第一份文档后，这里会变成你的工作台列表。</p>
    </section>

    <DocumentList
      v-else
      :documents="documents"
      :highlighted-document-id="highlightedDocumentId"
      @open="openDocument"
    />
  </main>
</template>

<style scoped>
.library-shell {
  display: grid;
  gap: 18px;
  padding-bottom: 28px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.9fr);
  gap: 16px;
}

.library-side-stack {
  display: grid;
  gap: 16px;
}

.context-card,
.recent-card,
.toolbar-panel {
  display: grid;
  gap: 16px;
}

.context-card h2,
.recent-card h2 {
  margin: 6px 0 0;
  font-size: 24px;
}

.section-header.compact h2 {
  margin: 6px 0 0;
  font-size: 22px;
}

.recent-grid {
  display: grid;
  gap: 10px;
}

.recent-item {
  border: 1px solid var(--surface-border);
  border-radius: 18px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
}

.recent-title,
.recent-meta {
  display: block;
}

.recent-title {
  font-weight: 600;
}

.recent-meta {
  margin-top: 6px;
  color: var(--muted-soft);
  font-size: 13px;
}

.toolbar-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 2fr) repeat(5, minmax(120px, 1fr)) auto;
  align-items: end;
}

.field-grid {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: var(--muted-strong);
}

.field-grid.wide {
  min-width: 0;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.inline-state {
  display: grid;
  gap: 12px;
  justify-items: start;
}

.success-banner {
  border-color: rgba(16, 110, 84, 0.18);
  background: rgba(238, 249, 242, 0.92);
}

@media (max-width: 1180px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .toolbar-grid {
    grid-template-columns: 1fr;
  }
}
</style>
