<script setup>
const props = defineProps({
  documents: {
    type: Array,
    default: () => []
  },
  highlightedDocumentId: {
    type: String,
    default: ""
  },
  deletingDocumentId: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["preview", "edit", "delete", "start-edit"]);

function startEdit() {
  emit("start-edit");
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

function statusLabel(document) {
  if (!document.storageAvailable) {
    return "存储异常";
  }

  return {
    draft: "草稿",
    editing: "编辑中",
    saved: "已保存",
    failed: "保存失败",
    archived: "已归档"
  }[document.status] ?? document.status;
}

function statusTone(document) {
  if (!document.storageAvailable) {
    return { type: "danger", effect: "dark" };
  }

  const toneMap = {
    draft: { type: "info", effect: "light" },
    editing: { type: "primary", effect: "light" },
    saved: { type: "success", effect: "light" },
    failed: { type: "danger", effect: "light" },
    archived: { type: "info", effect: "plain" }
  };
  return toneMap[document.status] ?? { type: "info", effect: "plain" };
}

function previewDocument(document) {
  emit("preview", document);
}

function editDocument(document) {
  emit("edit", document);
}

function deleteDocument(document) {
  emit("delete", document);
}

function tableRowClassName({ row }) {
  if (row.documentId === props.highlightedDocumentId) {
    return "highlighted-row";
  }
  return "";
}
</script>

<template>
  <el-card shadow="never" class="list-panel">
    <template #header>
      <div class="section-heading">
        <div class="heading-left">
          <el-button type="primary" class="start-edit-btn" @click="startEdit">开始编辑</el-button>
          <div class="heading-titles">
            <p class="eyebrow">文档列表</p>
            <p class="muted-copy intro-copy">先查看，再决定是否进入编辑</p>
          </div>
        </div>
        <p class="muted-copy helper-copy">点击行可直接预览；“编辑”会进入独立可编辑工作台。</p>
      </div>
    </template>

    <el-table
      :data="documents"
      class="document-table"
      style="width: 100%"
      :row-class-name="tableRowClassName"
      @row-click="previewDocument"
    >
      <el-table-column prop="title" label="文档标题" min-width="200">
        <template #default="{ row }">
          <div style="font-weight: 600; cursor: pointer;">{{ row.title }}</div>
          <div style="font-size: 12px; color: var(--el-text-color-secondary);">ID: {{ row.documentId }}</div>
        </template>
      </el-table-column>
      
      <el-table-column label="状态" width="220">
        <template #default="{ row }">
          <el-tag :type="statusTone(row).type" :effect="statusTone(row).effect" size="small" style="margin-right: 4px; margin-bottom: 4px;">
            {{ statusLabel(row) }}
          </el-tag>
          <el-tag v-if="row.documentType" type="info" size="small" style="margin-right: 4px; margin-bottom: 4px;">
            {{ row.documentType }}
          </el-tag>
          <el-tag v-if="row.sourceSystem && row.sourceSystem !== 'native'" type="warning" size="small" style="margin-bottom: 4px;">
            来源: {{ row.sourceSystem }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="最近编辑" width="180">
        <template #default="{ row }">
          <span style="font-size: 13px;">{{ formatTimestamp(row.lastEditedTime) }}</span>
        </template>
      </el-table-column>
      
      <el-table-column label="所有者 / 访问者" width="180">
        <template #default="{ row }">
          <div style="font-size: 13px;">Owner: {{ row.ownerUser || '暂无' }}</div>
          <div style="font-size: 13px; color: var(--el-text-color-secondary);">Actor: {{ row.actorName || row.actorUser || '暂无' }}</div>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" class="preview-document-btn" @click.stop="previewDocument(row)">查看</el-button>
          <el-button size="small" type="primary" class="edit-document-btn" @click.stop="editDocument(row)">编辑</el-button>
          <el-button
            size="small"
            type="danger"
            plain
            class="delete-document-btn"
            :loading="deletingDocumentId === row.documentId"
            @click.stop="deleteDocument(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.list-panel {
  border-radius: var(--el-border-radius-base);
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.list-panel :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  padding-top: 0;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.heading-left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0 0 4px;
}

.heading-titles {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.muted-copy {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin: 0;
}

.intro-copy,
.helper-copy {
  line-height: 1.5;
}

.document-table {
  flex: 1;
  min-height: 0;
  min-width: 0;
}

/* Custom row class styling via deep selector for Element Plus Table */
:deep(.el-table .highlighted-row) {
  --el-table-tr-bg-color: var(--el-color-success-light-9);
}
:deep(.el-table .el-table__row) {
  cursor: pointer;
}

@media (max-width: 767px) {
  .section-heading {
    flex-direction: column;
  }
}
</style>
