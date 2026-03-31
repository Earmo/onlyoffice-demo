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

const isCreating = ref(false);
const isUploading = ref(false);
const isImporting = ref(false);

// 搜索与筛选条件统一放在页面层，列表组件只负责展示，不自己持有筛选逻辑。
// 这样创建/上传/导入成功后，页面可以主动重置条件并回到“全部文档”视图。
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
    const response = await apiFetch(`/api/documents${suffix}`);
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
  <el-container class="library-shell">
    <el-main>
      <el-row :gutter="16" class="hero-grid">
        <el-col :xs="24" :sm="16">
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
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-card class="context-card" shadow="hover" style="margin-bottom: 16px;">
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

          <el-card class="recent-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="eyebrow">最近文档</span>
                <h2>继续上次工作</h2>
              </div>
            </template>
            <div v-if="recentDocuments.length" class="recent-grid">
              <el-card v-for="document in recentDocuments" :key="document.documentId" shadow="never" class="recent-item">
                <div class="recent-copy">
                  <span class="recent-title">{{ document.title }}</span>
                  <span class="recent-meta">{{ formatTimestamp(document.lastSavedTime) }}</span>
                </div>
                <div class="recent-actions" style="margin-top: 8px;">
                  <el-button size="small" @click="previewDocument(document)">查看文件</el-button>
                  <el-button size="small" type="primary" @click="editDocument(document)">编辑文档</el-button>
                </div>
              </el-card>
            </div>
            <p v-else class="muted-copy">还没有最近文档，先创建或上传第一份内容。</p>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="toolbar-panel" shadow="never" style="margin-top: 16px; margin-bottom: 16px;">
        <el-form :inline="true" @submit.prevent="applyFilters">
          <el-form-item label="搜索文档">
            <el-input v-model="searchQuery" placeholder="按标题、ID 搜索" clearable style="width: 200px;" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="statusFilter" style="width: 120px;">
              <el-option label="全部状态" value="all" />
              <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档类型">
            <el-select v-model="documentTypeFilter" style="width: 120px;">
              <el-option label="全部类型" value="all" />
              <el-option v-for="t in documentTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源系统">
            <el-select v-model="sourceSystemFilter" style="width: 120px;">
              <el-option label="全部来源" value="all" />
              <el-option v-for="s in sourceSystemOptions" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象可用性">
            <el-select v-model="storageFilter" style="width: 120px;">
              <el-option label="全部" value="all" />
              <el-option label="仅可用" value="available" />
              <el-option label="仅异常" value="unavailable" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-select v-model="sortDirection" style="width: 120px;">
              <el-option label="最近优先" value="desc" />
              <el-option label="较早优先" value="asc" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="isLoading">搜索与筛选</el-button>
            <el-button @click="resetFilters" :disabled="isLoading">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon style="margin-bottom: 16px;" />
      
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon style="margin-bottom: 16px;">
        <el-button size="small" @click="loadDocuments" style="margin-top: 8px;">重新加载</el-button>
      </el-alert>

      <el-empty v-else-if="isLoading" description="正在加载文档工作台..." />

      <el-empty v-else-if="!documents.length && hasActiveQuery" description="当前搜索或筛选条件下没有找到文档。">
        <el-button @click="resetFilters">清空筛选</el-button>
      </el-empty>

      <el-empty v-else-if="!documents.length" description="当前租户下还没有任何文档。从上方主操作区新建或上传第一份文档。" />

      <DocumentList
        v-else
        :documents="documents"
        :highlighted-document-id="highlightedDocumentId"
        @preview="previewDocument"
        @edit="editDocument"
      />
    </el-main>
  </el-container>
</template>

<style scoped>
.library-shell {
  min-height: 100vh;
  background-color: transparent;
}
.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}
.card-header h2 {
  margin: 4px 0 0;
  font-size: 18px;
}
.muted-copy {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.recent-item {
  margin-bottom: 8px;
}
.recent-title {
  display: block;
  font-weight: bold;
}
.recent-meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
</style>
