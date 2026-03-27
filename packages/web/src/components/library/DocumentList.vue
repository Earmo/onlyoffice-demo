<script setup>
const props = defineProps({
  documents: {
    type: Array,
    default: () => []
  },
  highlightedDocumentId: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["preview", "edit"]);

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
  // storageAvailable 为 false 时，优先把问题表达成“存储异常”，
  // 这样列表页能第一时间暴露对象丢失或存储不可用等异常，而不是只显示业务状态。
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
    return "is-error";
  }

  return {
    draft: "is-muted",
    editing: "is-progress",
    saved: "is-success",
    failed: "is-error",
    archived: "is-muted"
  }[document.status] ?? "is-muted";
}

function previewDocument(document) {
  emit("preview", document);
}

function editDocument(document) {
  emit("edit", document);
}

function handleKeyboardPreview(event, document) {
  // 整行继续保留键盘可达性，但 Phase 9 起整行行为明确为“查看文件”，
  // 编辑动作则由单独按钮承担，避免整行点击就把用户送进可编辑工作台。
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    previewDocument(document);
  }
}
</script>

<template>
  <section class="surface-panel list-panel">
    <div class="section-heading">
      <div>
        <p class="eyebrow">文档列表</p>
        <h2>先查看，再决定是否进入编辑</h2>
      </div>
      <p class="muted-copy">整行可直接预览文档；“编辑文档”会进入独立可编辑工作台。</p>
    </div>

    <div class="document-grid" role="list">
      <article
        v-for="document in documents"
        :key="document.documentId"
        class="document-row"
        :class="{ highlighted: highlightedDocumentId === document.documentId }"
        role="button"
        tabindex="0"
        @click="previewDocument(document)"
        @keydown="handleKeyboardPreview($event, document)"
      >
        <div class="document-main">
          <div class="document-title-row">
            <h3>{{ document.title }}</h3>
            <span class="status-chip" :class="statusTone(document)">
              {{ statusLabel(document) }}
            </span>
            <span v-if="document.documentType" class="status-chip is-outline">
              {{ document.documentType }}
            </span>
            <span v-if="document.sourceSystem && document.sourceSystem !== 'native'" class="status-chip is-outline">
              来源 {{ document.sourceSystem }}
            </span>
          </div>

          <p class="document-meta">
            <span>documentId: <code>{{ document.documentId }}</code></span>
            <span>最近保存：<code>{{ formatTimestamp(document.lastSavedTime) }}</code></span>
          </p>

          <p class="document-meta secondary">
            <span>owner：<code>{{ document.ownerUser || "暂无" }}</code></span>
            <span>当前访问者：<code>{{ document.actorName || document.actorUser || "暂无" }}</code></span>
          </p>
        </div>

        <div class="document-actions">
          <button class="ghost-button secondary compact" type="button" @click.stop="previewDocument(document)">
            查看文件
          </button>
          <button class="ghost-button compact" type="button" @click.stop="editDocument(document)">
            编辑文档
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.list-panel {
  display: grid;
  gap: 18px;
}

.section-heading {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: flex-end;
}

.section-heading h2 {
  margin: 6px 0 0;
  font-size: 26px;
}

.document-grid {
  display: grid;
  gap: 12px;
}

.document-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid var(--surface-border);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.document-row:hover,
.document-row:focus-visible {
  transform: translateY(-2px);
  box-shadow: 0 18px 36px rgba(20, 28, 36, 0.08);
  border-color: rgba(139, 94, 52, 0.32);
  outline: none;
}

.document-row.highlighted {
  border-color: rgba(16, 110, 84, 0.28);
  box-shadow: 0 18px 42px rgba(16, 110, 84, 0.12);
  background: linear-gradient(180deg, rgba(235, 248, 242, 0.92) 0%, rgba(255, 255, 255, 0.86) 100%);
}

.document-main {
  display: grid;
  gap: 10px;
}

.document-title-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.document-title-row h3 {
  margin: 0;
  font-size: 18px;
}

.document-meta {
  margin: 0;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: var(--muted-strong);
  font-size: 13px;
}

.document-meta.secondary {
  color: var(--muted-soft);
}

.document-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: end;
}

@media (max-width: 860px) {
  .section-heading {
    display: grid;
    gap: 8px;
  }

  .document-row {
    grid-template-columns: 1fr;
  }

  .document-actions {
    justify-content: start;
  }
}
</style>
