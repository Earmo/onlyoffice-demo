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

// 预览页沿用和编辑页相同的 documentId 路由语义，
// 但整个页面明确保持只读，不建立编辑心跳和右侧运行台。
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
  // 预览页只需要文档摘要信息，不需要像编辑页那样同时拉最近文档列表。
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



function goToEditor() {
  // 预览和编辑是显式切换，不在当前页内部热切模式，便于保持会话边界清晰。
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
  <el-container class="preview-page-shell" direction="vertical">
    <el-card shadow="never" class="preview-header-card" style="margin-bottom: 16px;">
      <div class="preview-header">
        <div class="preview-copy">
          <p class="eyebrow">只读预览</p>
          <h1>{{ currentDocument?.title || currentDocumentId }}</h1>
          <p class="muted-copy">
            这里用于安全查看文档内容，不建立活跃编辑会话；若需要修改，请进入独立编辑工作台。
          </p>
        </div>
        <div class="preview-actions">

           <el-button type="primary" :disabled="isLoading" @click="goToEditor">编辑文档</el-button>
        </div>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon style="margin-bottom: 16px;">
      <el-button size="small" @click="loadPreviewPageData" style="margin-top: 8px;">重新加载</el-button>
    </el-alert>

    <el-empty v-else-if="isLoading" description="正在加载预览页..." />

    <el-row v-else :gutter="16" class="preview-layout" style="flex: 1; min-height: 0;">
      <el-col :xs="24" :md="16" :lg="18" class="preview-stage-col">
        <div class="preview-stage">
          <EditorShell
            :document-id="currentDocumentId"
            :document-title="currentDocument?.title || currentDocumentId"
            :readonly="true"
            :show-console="false"
          />
        </div>
      </el-col>

      <el-col :xs="24" :md="8" :lg="6">
        <el-card shadow="never" class="preview-sidebar">
          <div class="preview-meta-section" style="margin-bottom: 24px;">
            <p class="eyebrow">文档信息</p>
            <h2 style="margin: 4px 0 8px; font-size: 18px;">{{ currentDocument?.title || "未命名文档" }}</h2>
            <p class="muted-copy">最近保存：<code>{{ formatTimestamp(currentDocument?.lastSavedTime) }}</code></p>
            <p class="muted-copy">当前状态：<el-tag size="small">{{ currentDocument?.status || "未知" }}</el-tag></p>
            <p class="muted-copy">documentId：<code>{{ currentDocumentId }}</code></p>
          </div>

          <div class="preview-meta-section">
            <p class="eyebrow">下一步</p>
            <p class="muted-copy" style="margin-bottom: 12px;">
              预览页只负责查看内容，不会显示编辑控制台，也不会建立“编辑中”会话状态。
            </p>
            <el-button type="primary" plain @click="goToEditor">
              进入编辑工作台
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-container>
</template>

<style scoped>
.preview-page-shell {
  min-height: 100vh;
  padding: 18px;
  background-color: var(--el-bg-color-page);
  display: flex;
  flex-direction: column;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.preview-copy h1 {
  margin: 4px 0 8px;
  font-size: 24px;
}

.preview-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0;
}

.muted-copy {
  margin: 4px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.preview-layout {
  display: flex;
}

.preview-stage-col {
  display: flex;
  flex-direction: column;
}

.preview-stage {
  flex: 1;
  min-height: 600px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
}

.preview-sidebar {
  position: sticky;
  top: 18px;
}
</style>
