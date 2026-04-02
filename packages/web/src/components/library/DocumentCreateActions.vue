<script setup>
import { ref } from "vue";
import { Plus, UploadFilled, Link as LinkIcon } from "@element-plus/icons-vue";

const props = defineProps({
  isCreating: {
    type: Boolean,
    default: false
  },
  isUploading: {
    type: Boolean,
    default: false
  },
  isImporting: {
    type: Boolean,
    default: false
  },
  remoteDocumentUrl: {
    type: String,
    default: ""
  }
});

const emit = defineEmits([
  "create",
  "file-selected",
  "import-remote",
  "update:remoteDocumentUrl"
]);

// 这个组件故意不直接发请求，而是只负责“采集用户意图并向父层抛事件”。
// 这样页面层可以统一处理 loading、成功提示、错误提示和回流高亮语义。
const fileInputRef = ref(null);

function openFilePicker() {
  fileInputRef.value?.click();
}

function handleFileSelected(event) {
  // 原生 file input 不会在选择同一个文件时重复触发 change，
  // 所以这里在抛出文件后手动清空 value，保证用户重复上传同一文件也能生效。
  const file = event.target.files?.[0];
  if (file) {
    emit("file-selected", file);
  }
  event.target.value = "";
}
</script>

<template>
  <el-card shadow="never" class="actions-panel">
    <template #header>
      <div class="section-header">
        <p class="eyebrow">文档入口</p>
        <h2 style="margin: 0; font-size: 20px;">开始新的编辑流程</h2>
        <p class="muted-copy" style="margin: 8px 0 0;">
          首页直接提供新建、上传和远程导入三类入口，所有结果都会先回到工作台列表，再由你决定进入哪份文档。
        </p>
      </div>
    </template>

    <div class="action-grid" style="margin-bottom: 24px;">
      <el-button type="primary" :loading="isCreating" @click="$emit('create')" size="large">
        <el-icon v-if="!isCreating" style="margin-right: 6px;"><Plus /></el-icon>
        新建空白文档
      </el-button>

      <input
        ref="fileInputRef"
        class="hidden-file-input"
        type="file"
        accept=".doc,.docx,.odt,.rtf,.txt,.xls,.xlsx,.ods,.csv,.ppt,.pptx,.odp,.pdf"
        @change="handleFileSelected"
        style="display: none;"
      />
      <el-button :loading="isUploading" @click="openFilePicker" size="large">
        <el-icon v-if="!isUploading" style="margin-right: 6px;"><UploadFilled /></el-icon>
        上传本地文档
      </el-button>
    </div>

    <el-form label-position="top">
      <el-form-item label="远程文档地址">
        <el-input
          :model-value="remoteDocumentUrl"
          type="url"
          placeholder="https://example.com/roadmap.docx"
          :disabled="isImporting"
          @update:model-value="$emit('update:remoteDocumentUrl', $event)"
        >
          <template #prefix>
            <el-icon><LinkIcon /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      
      <el-form-item style="margin-bottom: 0;">
        <el-button
          type="primary"
          plain
          :loading="isImporting"
          :disabled="!props.remoteDocumentUrl"
          @click="$emit('import-remote')"
        >
          导入网络文档
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.actions-panel {
  border-radius: var(--el-border-radius-base);
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0 0 4px;
}

.muted-copy {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.action-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
</style>
