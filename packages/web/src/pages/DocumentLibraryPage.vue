<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import DocumentCreateActions from "../components/library/DocumentCreateActions.vue";
import DocumentList from "../components/library/DocumentList.vue";
import { apiFetch } from "../lib/api";

const route = useRoute();
const router = useRouter();

// 这里维护的是“工作台首页”级别状态：
// - documents 是当前列表结果；
// - tenant/actor 信息用于顶部上下文提示；
// - success/error/highlight 用于处理创建回流、错误反馈和列表定位。
const documents = ref([]);
const tenantId = ref("");
const actorUser = ref("");
const actorName = ref("");
const isLoading = ref(true);
const errorMessage = ref("");
const successMessage = ref("");
const highlightedDocumentId = ref("");
const remoteDocumentUrl = ref("");
const showCreateDialog = ref(false);

const isCreating = ref(false);
const isUploading = ref(false);
const isImporting = ref(false);
const deletingDocumentId = ref("");
const pageNumber = ref(1);
const pageSize = ref(10);
const total = ref(0);
const totalPages = ref(0);

// 搜索与筛选条件统一放在页面层，列表组件只负责展示，不自己持有筛选逻辑。
// 这样创建/上传/导入成功后，页面可以主动重置条件并回到“全部文档”视图。
const searchQuery = ref("");
const statusFilter = ref("all");
const documentTypeFilter = ref("all");
const sourceSystemFilter = ref("");
const storageFilter = ref("all");

const recentDocuments = ref([]);
const statusOptions = [
  { label: "草稿", value: "draft" },
  { label: "编辑中", value: "editing" },
  { label: "已保存", value: "saved" },
  { label: "保存失败", value: "failed" }
];
const documentTypeOptions = [
  { label: "文本文档", value: "word" },
  { label: "电子表格", value: "cell" },
  { label: "演示文稿", value: "slide" },
  { label: "PDF", value: "pdf" }
];

const hasActiveQuery = computed(() => {
  return Boolean(
    searchQuery.value
      || statusFilter.value !== "all"
      || documentTypeFilter.value !== "all"
      || sourceSystemFilter.value
      || storageFilter.value !== "all"
  );
});

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
  params.set("pageNumber", String(pageNumber.value));
  params.set("pageSize", String(pageSize.value));
  if (searchQuery.value) {
    params.set("query", searchQuery.value);
  }
  if (statusFilter.value !== "all") {
    params.set("status", statusFilter.value);
  }
  if (documentTypeFilter.value !== "all") {
    params.set("documentType", documentTypeFilter.value);
  }
  if (sourceSystemFilter.value) {
    params.set("sourceSystem", sourceSystemFilter.value);
  }
  if (storageFilter.value !== "all") {
    params.set("storage", storageFilter.value);
  }
  return params;
}

async function loadDocuments() {
  const params = buildListParams();
  const suffix = params.toString() ? `?${params.toString()}` : "";
  const response = await apiFetch(`/api/documents${suffix}`);
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `文档列表加载失败，HTTP ${response.status}`));
  }

  const payload = await response.json();
  documents.value = payload.documents ?? [];
  tenantId.value = payload.tenantId ?? "";
  actorUser.value = payload.actorUser ?? "";
  actorName.value = payload.actorName ?? "";
  pageNumber.value = payload.pageNumber ?? pageNumber.value;
  pageSize.value = payload.pageSize ?? pageSize.value;
  total.value = payload.total ?? documents.value.length;
  totalPages.value = payload.totalPages ?? 0;
}

async function loadRecentDocuments() {
  const response = await apiFetch("/api/documents/recent?limit=3");
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `最近文档加载失败，HTTP ${response.status}`));
  }

  const payload = await response.json();
  recentDocuments.value = Array.isArray(payload) ? payload : [];
}

function normalizeErrorMessage(error, fallbackMessage) {
  return error instanceof Error ? error.message : fallbackMessage;
}

async function loadLibraryWorkspace() {
  isLoading.value = true;
  errorMessage.value = "";

  try {
    await Promise.all([loadDocuments(), loadRecentDocuments()]);
  } catch (error) {
    errorMessage.value = normalizeErrorMessage(error, "文档工作台加载失败");
  } finally {
    isLoading.value = false;
  }
}

async function reloadDocumentList() {
  isLoading.value = true;
  errorMessage.value = "";

  try {
    await loadDocuments();
  } catch (error) {
    errorMessage.value = normalizeErrorMessage(error, "文档列表加载失败");
  } finally {
    isLoading.value = false;
  }
}

async function refreshWorkspaceAfterMutation() {
  await loadLibraryWorkspace();
  if (!documents.value.length && pageNumber.value > 1 && totalPages.value > 0) {
    pageNumber.value = Number(totalPages.value);
    await loadLibraryWorkspace();
  }
}

function syncHighlightFromRoute() {
  const highlight = route.query.highlight;
  highlightedDocumentId.value = typeof highlight === "string" ? highlight : "";
}

async function revealDocument(documentSummary, message) {
  showCreateDialog.value = false;
  searchQuery.value = "";
  statusFilter.value = "all";
  documentTypeFilter.value = "all";
  sourceSystemFilter.value = "";
  storageFilter.value = "all";
  pageNumber.value = 1;
  highlightedDocumentId.value = documentSummary.documentId;
  successMessage.value = message;
  await router.replace({ path: "/", query: { highlight: documentSummary.documentId } });
  await loadLibraryWorkspace();
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
    const response = await apiFetch("/api/documents", {
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

    const response = await apiFetch("/api/documents/upload", {
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
    const response = await apiFetch("/api/documents/import-remote", {
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

function previewDocument(document) {
  router.push({ name: "preview", params: { documentId: document.documentId } });
}

function editDocument(document) {
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
  pageNumber.value = 1;
  await reloadDocumentList();
}

async function resetFilters() {
  searchQuery.value = "";
  statusFilter.value = "all";
  documentTypeFilter.value = "all";
  sourceSystemFilter.value = "";
  storageFilter.value = "all";
  pageNumber.value = 1;
  successMessage.value = "";
  await router.replace({ path: "/", query: {} });
  await reloadDocumentList();
}

async function handlePageNumberChange(nextPageNumber) {
  pageNumber.value = nextPageNumber;
  successMessage.value = "";
  await reloadDocumentList();
}

async function handlePageSizeChange(nextPageSize) {
  pageSize.value = nextPageSize;
  pageNumber.value = 1;
  successMessage.value = "";
  await reloadDocumentList();
}

async function deleteDocument(document) {
  if (!document || deletingDocumentId.value) {
    return;
  }

  const confirmed = window.confirm(`确认删除《${document.title}》吗？删除后它不会再出现在文档列表和最近文档中。`);
  if (!confirmed) {
    return;
  }

  deletingDocumentId.value = document.documentId;
  successMessage.value = "";
  errorMessage.value = "";

  try {
    const response = await apiFetch(`/api/documents/${document.documentId}`, {
      method: "DELETE"
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `删除文档失败，HTTP ${response.status}`));
    }

    if (highlightedDocumentId.value === document.documentId) {
      highlightedDocumentId.value = "";
      await router.replace({ path: "/", query: {} });
    }

    successMessage.value = `已删除 ${document.title}。`;
    await refreshWorkspaceAfterMutation();
  } catch (error) {
    errorMessage.value = normalizeErrorMessage(error, "删除文档失败");
  } finally {
    deletingDocumentId.value = "";
  }
}

watch(
  () => route.query.highlight,
  () => {
    syncHighlightFromRoute();
  },
  { immediate: true }
);

onMounted(loadLibraryWorkspace);
</script>

<template>
  <el-container class="library-shell">
    <el-main class="library-main">
      <el-row :gutter="24" class="library-grid">
        <!-- 左侧栏：当前上下文与最近文档 -->
        <el-col :xs="24" :lg="5" class="library-column">
          <div class="column-stack column-stack-left">
            <el-card class="context-card library-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="eyebrow">当前上下文</span>
                  <h2>文档工作台</h2>
                </div>
              </template>
              <p class="muted-copy">
                当前租户 <el-tag size="small">{{ tenantId || "未解析" }}</el-tag>，当前用户
                <el-tag size="small" type="info">{{ actorName || actorUser || "未解析" }}</el-tag>。
              </p>
            </el-card>

            <el-card class="recent-card library-card library-card-grow" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="eyebrow">最近文档</span>
                  <h2>继续上次工作</h2>
                </div>
              </template>
              <div v-if="recentDocuments.length" class="recent-grid">
                <el-card
                  v-for="document in recentDocuments"
                  :key="document.documentId"
                  shadow="never"
                  class="recent-item"
                >
                  <div class="recent-copy">
                    <span class="recent-title">{{ document.title }}</span>
                    <span class="recent-meta">最近编辑：{{ formatTimestamp(document.lastEditedTime) }}</span>
                  </div>
                  <div class="recent-actions">
                    <el-button size="small" @click="previewDocument(document)">查看文件</el-button>
                    <el-button size="small" type="primary" @click="editDocument(document)">编辑文档</el-button>
                  </div>
                </el-card>
              </div>
              <p v-else class="muted-copy">还没有最近文档，先创建或上传第一份内容。</p>
            </el-card>
          </div>
        </el-col>

        <!-- 右侧栏：搜索条件与文档列表 -->
        <el-col :xs="24" :lg="19" class="library-column">
          <div class="column-stack column-stack-right">
            <el-card class="toolbar-panel library-card" shadow="never">
              <el-form :inline="true" @submit.prevent="applyFilters">
                <el-form-item label="搜索文档">
                  <el-input v-model="searchQuery" placeholder="按标题、ID 搜索" clearable style="width: 200px;" />
                </el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="statusFilter" style="width: 120px;">
                    <el-option label="全部状态" value="all" />
                    <el-option
                      v-for="status in statusOptions"
                      :key="status.value"
                      :label="status.label"
                      :value="status.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="文档类型">
                  <el-select v-model="documentTypeFilter" style="width: 120px;">
                    <el-option label="全部类型" value="all" />
                    <el-option
                      v-for="type in documentTypeOptions"
                      :key="type.value"
                      :label="type.label"
                      :value="type.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="来源系统">
                  <el-input
                    v-model="sourceSystemFilter"
                    placeholder="如 native / oa"
                    clearable
                    style="width: 140px;"
                  />
                </el-form-item>
                <el-form-item label="对象可用性">
                  <el-select v-model="storageFilter" style="width: 120px;">
                    <el-option label="全部" value="all" />
                    <el-option label="仅可用" value="available" />
                    <el-option label="仅异常" value="unavailable" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" native-type="submit" :loading="isLoading">搜索与筛选</el-button>
                  <el-button @click="resetFilters" :disabled="isLoading">重置</el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <div class="results-stack">
              <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon class="stack-alert" />

              <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon class="stack-alert">
                <el-button size="small" @click="loadLibraryWorkspace" style="margin-top: 8px;">重新加载</el-button>
              </el-alert>

              <div class="results-panel">
                <el-empty v-if="isLoading" description="正在加载文档工作台..." class="library-empty" />

                <el-empty
                  v-else-if="!documents.length && hasActiveQuery"
                  description="当前搜索或筛选条件下没有找到文档。"
                  class="library-empty"
                >
                  <el-button @click="resetFilters">清空筛选</el-button>
                </el-empty>

                <el-empty
                  v-else-if="!documents.length"
                  description="当前租户下还没有任何文档。可以开始新建或上传第一份文档。"
                  class="library-empty"
                >
                  <el-button type="primary" @click="showCreateDialog = true" class="start-edit-empty-btn">开始编辑</el-button>
                </el-empty>

                <DocumentList
                  v-else
                  class="document-list-block"
                  :documents="documents"
                  :highlighted-document-id="highlightedDocumentId"
                  :deleting-document-id="deletingDocumentId"
                  @preview="previewDocument"
                  @edit="editDocument"
                  @delete="deleteDocument"
                  @start-edit="showCreateDialog = true"
                />
              </div>

              <el-pagination
                v-if="total > 0"
                v-model:current-page="pageNumber"
                v-model:page-size="pageSize"
                class="library-pagination"
                :total="total"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @current-change="handlePageNumberChange"
                @size-change="handlePageSizeChange"
              />
            </div>
          </div>
        </el-col>
      </el-row>

      <el-dialog v-model="showCreateDialog" width="640px" destroy-on-close>
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
      </el-dialog>
    </el-main>
  </el-container>
</template>

<style scoped>
.library-shell {
  min-height: 100vh;
  background-color: transparent;
}

.library-main {
  min-height: 100vh;
}

.library-grid {
  min-height: calc(100vh - 40px);
  align-items: stretch;
}

.library-column {
  display: flex;
}

.column-stack {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.column-stack-right {
  width: 100%;
}

.library-card {
  width: 100%;
}

.library-card-grow {
  flex: 1;
}

.library-card-grow :deep(.el-card__body) {
  height: 100%;
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-header h2 {
  margin: 0;
  font-size: 18px;
}

.muted-copy {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.recent-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recent-grid {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
}

.recent-item {
  border: 1px solid var(--el-border-color-lighter);
}

.recent-item :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recent-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.recent-title {
  display: block;
  font-weight: 600;
}

.recent-meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.recent-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.results-stack {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.stack-alert {
  flex: 0 0 auto;
}

.results-panel {
  display: flex;
  flex: 1;
  min-height: 0;
}

.document-list-block {
  flex: 1;
  min-height: 100%;
}

.library-empty {
  width: 100%;
  align-self: stretch;
  border: 1px dashed var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
}

.library-pagination {
  justify-content: flex-end;
}

@media (max-width: 1199px) {
  .library-main {
    min-height: auto;
  }

  .library-grid {
    min-height: auto;
  }
}
</style>
