<script setup>
import { ref } from "vue";

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
  <section class="surface-panel actions-panel">
    <div class="section-header">
      <p class="eyebrow">文档入口</p>
      <h2>开始新的编辑流程</h2>
      <p class="muted-copy">
        首页直接提供新建、上传和远程导入三类入口，所有结果都会先回到工作台列表，再由你决定进入哪份文档。
      </p>
    </div>

    <div class="action-grid">
      <button class="ghost-button primary" type="button" :disabled="isCreating" @click="$emit('create')">
        {{ isCreating ? "创建中..." : "新建空白文档" }}
      </button>

      <input
        ref="fileInputRef"
        class="hidden-file-input"
        type="file"
        accept=".doc,.docx,.odt,.rtf,.txt,.xls,.xlsx,.ods,.csv,.ppt,.pptx,.odp,.pdf"
        @change="handleFileSelected"
      />
      <button class="ghost-button" type="button" :disabled="isUploading" @click="openFilePicker">
        {{ isUploading ? "上传中..." : "上传本地文档" }}
      </button>
    </div>

    <label class="field-grid">
      <span>远程文档地址</span>
      <input
        :value="remoteDocumentUrl"
        class="surface-input"
        type="url"
        placeholder="https://example.com/roadmap.docx"
        :disabled="isImporting"
        @input="$emit('update:remoteDocumentUrl', $event.target.value)"
      />
    </label>

    <button
      class="ghost-button secondary"
      type="button"
      :disabled="isImporting || !props.remoteDocumentUrl"
      @click="$emit('import-remote')"
    >
      {{ isImporting ? "导入中..." : "导入网络文档" }}
    </button>
  </section>
</template>

<style scoped>
.actions-panel {
  display: grid;
  gap: 18px;
}

.section-header {
  display: grid;
  gap: 10px;
}

.section-header h2 {
  margin: 0;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.02;
}

.action-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.field-grid {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: var(--muted-strong);
}
</style>
