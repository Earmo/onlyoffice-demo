<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft } from "@element-plus/icons-vue";
import EditorShell from "../components/editor/EditorShell.vue";
import { apiFetch } from "../lib/api";

const route = useRoute();
const router = useRouter();

const isLoading = ref(true);
const errorMessage = ref("");
const currentDocument = ref(null);
const editorShellRef = ref(null);

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

const outlineTreeData = computed(() => editorShellRef.value?.outlineTreeData || []);
const isRefreshingOutline = computed(() => editorShellRef.value?.isRefreshingOutline || false);
const hasEmptyOutline = computed(() => editorShellRef.value?.hasEmptyOutline || false);
const activeHeadingId = computed(() => editorShellRef.value?.activeHeadingId || "");
const isOutlineSectionExpanded = ref(true);

function toggleOutlineSection() {
  isOutlineSectionExpanded.value = !isOutlineSectionExpanded.value;
}

async function refreshOutline() {
  await editorShellRef.value?.refreshOutline();
}

async function jumpToHeading(heading) {
  await editorShellRef.value?.jumpToHeading(heading);
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


    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon style="margin-bottom: 16px;">
      <el-button size="small" @click="loadPreviewPageData" style="margin-top: 8px;">重新加载</el-button>
    </el-alert>

    <el-empty v-else-if="isLoading" description="正在加载预览页..." />

    <el-row v-else :gutter="16" class="preview-layout" style="flex: 1; min-height: 0;">
      <el-col :xs="24" :md="16" :lg="18" class="preview-stage-col">
        <div class="preview-stage">
          <EditorShell
            ref="editorShellRef"
            :document-id="currentDocumentId"
            :document-title="currentDocument?.title || currentDocumentId"
            :readonly="true"
            :show-console="false"
          />
        </div>
      </el-col>

      <el-col :xs="24" :md="8" :lg="6">
        <el-card shadow="never" class="preview-sidebar" style="height: calc(100vh - 36px); display: flex; flex-direction: column;">
          <div class="preview-meta-section" style="margin-bottom: 24px;">
            <el-button link @click="router.push('/')" class="back-button">
              <el-icon style="margin-right: 4px;"><ArrowLeft /></el-icon> 返回文档列表
            </el-button>
            <p class="eyebrow" style="margin-top: 16px;">只读预览</p>
            <h1 style="margin: 4px 0 8px; font-size: 20px;">{{ currentDocument?.title || currentDocumentId }}</h1>
            <p class="muted-copy" style="margin-bottom: 16px;">
              这里用于安全查看文档内容，不建立活跃编辑会话；若需要修改，请进入独立编辑工作台。
            </p>
            
            <p class="muted-copy">最近保存：<code>{{ formatTimestamp(currentDocument?.lastSavedTime) }}</code></p>
            <p class="muted-copy">当前状态：<el-tag size="small">{{ currentDocument?.status || "未知" }}</el-tag></p>
            <p class="muted-copy">documentId：<code>{{ currentDocumentId }}</code></p>
          </div>

          <el-divider style="margin: 16px 0" />

          <div class="preview-section" style="flex: 1; overflow-y: auto;">
            <div class="sidebar-heading" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
              <div>
                <p class="eyebrow">当前文档</p>
                <h3 style="margin: 4px 0; font-size: 16px;">章节目录</h3>
              </div>
              <div style="display: flex; gap: 8px;">
                <el-button
                  size="small"
                  @click="refreshOutline"
                  :loading="isRefreshingOutline"
                  :disabled="isLoading"
                >
                  刷新
                </el-button>
                <el-button size="small" text @click="toggleOutlineSection">
                  {{ isOutlineSectionExpanded ? "收起" : "展开" }}
                </el-button>
              </div>
            </div>

            <div v-show="isOutlineSectionExpanded">

            <div v-if="outlineTreeData && outlineTreeData.length" class="outline-list">
              <el-tree
                :data="outlineTreeData"
                node-key="id"
                :current-node-key="activeHeadingId"
                highlight-current
                default-expand-all
                :expand-on-click-node="false"
                @node-click="jumpToHeading"
              >
                <template #default="{ node, data }">
                  <span class="custom-tree-node">
                    <span class="outline-level-tag">H{{ data.level }}</span>
                    <span class="outline-text-tag" :title="data.label">{{ data.label }}</span>
                  </span>
                </template>
              </el-tree>
            </div>
            <el-empty
              v-else-if="hasEmptyOutline"
              description="暂无章节标题"
              :image-size="60"
            />
            <p v-else class="muted-copy" style="text-align: center; margin-top: 24px;">
              等待文档加载完成后显示...
            </p>
            </div>
          </div>

          <el-divider style="margin: 16px 0" />

          <div class="preview-meta-section">
            <p class="eyebrow">下一步</p>
            <p class="muted-copy" style="margin-bottom: 12px;">
              预览页只负责查看内容，不会显示编辑控制台，也不会建立“编辑中”会话状态。
            </p>
            <el-button type="primary" style="width: 100%;" @click="goToEditor" :disabled="isLoading">
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
  height: 100vh;
  box-sizing: border-box;
  padding: 18px;
  background-color: var(--el-bg-color-page);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.back-button {
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
  margin: 4px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.preview-layout {
  display: flex;
  height: 100%;
}

.preview-stage-col {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.preview-stage {
  flex: 1;
  height: calc(100vh - 36px);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-sidebar {
  position: sticky;
  top: 18px;
}

.outline-list {
  display: grid;
  gap: 8px;
}

.outline-level-tag {
  display: inline-flex;
  min-width: 24px;
  justify-content: center;
  border-radius: 999px;
  background: var(--el-fill-color);
  padding: 2px 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.outline-text-tag {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  overflow: hidden;
}

.custom-tree-node .outline-text-tag {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
