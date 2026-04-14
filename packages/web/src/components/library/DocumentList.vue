<script setup>
// 纯展示型列表组件：
// - 接收文档摘要数据；
// - 把操作意图以事件形式抛给父层；
// - 不自行发请求，不持有业务状态。
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
  },
  // 当某行文档正在处理（转换/水印消除）时传入其 documentId，
  // 对应行的「更多▼」按钮会进入 loading 状态防止重复点击。
  processingDocumentId: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["preview", "edit", "delete", "start-edit", "convert", "remove-watermark"]);

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
  // storageAvailable 为 false 时优先显示"存储异常"，
  // 因为这类问题比普通业务状态更需要用户立即注意。
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
  // 行点击和"查看"按钮最终都走同一条 preview 事件。
  emit("preview", document);
}

function editDocument(document) {
  emit("edit", document);
}

// 判断是否为 Word 类型（docx / doc / odt）
function isWordFileType(fileType) {
  return ["docx", "doc", "odt"].includes(fileType);
}

// 下拉菜单指令路由：将 command 字符串映射到对应的 emit
function handleDropdownCommand(command, row) {
  if (command === "convert") {
    emit("convert", row);
  } else if (command === "remove-watermark") {
    emit("remove-watermark", row);
  } else if (command === "delete") {
    emit("delete", row);
  }
}

function tableRowClassName({ row }) {
  // 首页从创建/编辑流返回时，会通过 highlightedDocumentId 高亮目标文档。
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
      <!-- 标题列承担主阅读入口，同时补充 documentId 方便排查和复制。 -->
      <el-table-column prop="title" label="文档标题" min-width="200">
        <template #default="{ row }">
          <div style="font-weight: 600; cursor: pointer;">{{ row.title }}</div>
          <div style="font-size: 12px; color: var(--el-text-color-secondary);">ID: {{ row.documentId }}</div>
        </template>
      </el-table-column>
      
      <!-- 状态列把业务状态、文档类型、来源系统压在一个单元格里，减少横向占位。 -->
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
      
      <!-- 最近编辑时间是列表排序和回流判断的重要参考信息。 -->
      <el-table-column label="最近编辑" width="180">
        <template #default="{ row }">
          <span style="font-size: 13px;">{{ formatTimestamp(row.lastEditedTime) }}</span>
        </template>
      </el-table-column>
      
      <!-- 所有者 / 访问者并列展示，方便定位"谁创建、谁当前在操作"。 -->
      <el-table-column label="所有者 / 访问者" width="180">
        <template #default="{ row }">
          <div style="font-size: 13px;">Owner: {{ row.ownerUser || '暂无' }}</div>
          <div style="font-size: 13px; color: var(--el-text-color-secondary);">Actor: {{ row.actorName || row.actorUser || '暂无' }}</div>
        </template>
      </el-table-column>
      
      <!-- 操作列：「查看 / 编辑 / 更多▼」三按钮 -->
      <!-- 删除操作移入「更多▼」下拉菜单，与文档类型相关的操作（转换/去水印）也在菜单内。 -->
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" class="preview-document-btn" @click.stop="previewDocument(row)">查看</el-button>
          <el-button size="small" type="primary" class="edit-document-btn" @click.stop="editDocument(row)">编辑</el-button>
          <el-dropdown
            trigger="click"
            :disabled="processingDocumentId === row.documentId"
            @command="(cmd) => handleDropdownCommand(cmd, row)"
            @click.stop
          >
            <el-button
              size="small"
              plain
              class="more-document-btn"
              :loading="processingDocumentId === row.documentId"
              @click.stop
            >
              更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- PDF 类型：提供转换为 Word 入口 -->
                <el-dropdown-item v-if="row.fileType === 'pdf'" command="convert">
                  转换为 Word
                </el-dropdown-item>
                <!-- Word 类型（docx/doc/odt）：提供消除水印入口 -->
                <el-dropdown-item v-if="isWordFileType(row.fileType)" command="remove-watermark">
                  消除水印
                </el-dropdown-item>
                <!-- 删除是所有文档类型通用的危险操作 -->
                <el-dropdown-item divided command="delete" class="delete-document-item">
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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

.delete-document-item {
  color: var(--el-color-danger);
}

@media (max-width: 767px) {
  .section-heading {
    flex-direction: column;
  }
}
</style>
