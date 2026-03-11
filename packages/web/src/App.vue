<script setup>
import { onMounted, ref } from "vue";
import { DocumentEditor } from "@onlyoffice/document-editor-vue";

// 前端通过环境变量决定后端地址，默认直连本地 Spring Boot。
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

// 当前正在编辑的文档信息。
const currentDocumentId = ref("demo");
const currentDocumentTitle = ref("demo.docx");

// 当前页面支持在可编辑和只读两种模式之间切换。
const readonly = ref(false);
const imageUrl = ref("https://upload.wikimedia.org/wikipedia/commons/6/63/Wikipedia-logo.png");
const remoteDocumentUrl = ref("");
const isPanelOpen = ref(false);
const fileInputRef = ref(null);

// 页面状态分成三类：加载中、加载失败、拿到编辑器配置。
const isLoading = ref(true);
const isInsertingImage = ref(false);
const isImportingDocument = ref(false);
const errorMessage = ref("");
const editorPayload = ref(null);
const editorKey = ref(0);

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

async function loadEditorConfig() {
  // 每次重新加载前先重置页面状态，避免沿用上一次的报错或旧配置。
  isLoading.value = true;
  errorMessage.value = "";

  try {
    // 先从 Spring Boot 获取 ONLYOFFICE 初始化配置，而不是在前端手写 config。
    const params = new URLSearchParams({
      readonly: String(readonly.value)
    });
    const response = await fetch(
      `${apiBaseUrl}/api/documents/${currentDocumentId.value}/editor-config?${params.toString()}`
    );
    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `配置请求失败，HTTP ${response.status}`));
    }

    // 返回结果里同时包含 documentServerUrl 和 config，直接喂给官方组件即可。
    editorPayload.value = await response.json();
    // ONLYOFFICE 不适合直接热切换 view/edit，切换模式后用 key 强制重建实例更稳定。
    editorKey.value += 1;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "未知错误";
  } finally {
    isLoading.value = false;
  }
}

async function toggleReadonly() {
  readonly.value = !readonly.value;
  await loadEditorConfig();
}

function togglePanel() {
  isPanelOpen.value = !isPanelOpen.value;
}

function closePanel() {
  isPanelOpen.value = false;
}

function openFilePicker() {
  fileInputRef.value?.click();
}

async function switchToDocument(documentSummary) {
  currentDocumentId.value = documentSummary.documentId;
  currentDocumentTitle.value = documentSummary.title;
  readonly.value = false;
  await loadEditorConfig();
}

async function handleFileSelected(event) {
  const file = event.target.files?.[0];
  if (!file) {
    return;
  }

  isImportingDocument.value = true;
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
    await switchToDocument(documentSummary);
    closePanel();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "上传文档失败";
  } finally {
    isImportingDocument.value = false;
    event.target.value = "";
  }
}

async function importRemoteDocument() {
  isImportingDocument.value = true;
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
    await switchToDocument(documentSummary);
    closePanel();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "导入网络文档失败";
  } finally {
    isImportingDocument.value = false;
  }
}

function getDocEditorInstance() {
  return window.DocEditor?.instances?.docEditor;
}

async function insertRemoteImage() {
  if (readonly.value) {
    errorMessage.value = "只读模式下不能插入图片。";
    return;
  }

  const editor = getDocEditorInstance();
  if (!editor) {
    errorMessage.value = "编辑器尚未准备完成，请稍后再试。";
    return;
  }

  isInsertingImage.value = true;
  errorMessage.value = "";

  try {
    const response = await fetch(`${apiBaseUrl}/api/documents/${currentDocumentId.value}/images/insert`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        sourceUrl: imageUrl.value
      })
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response, `插图配置请求失败，HTTP ${response.status}`));
    }

    const payload = await response.json();
    editor.insertImage(payload.insertImage);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "插入图片失败";
  } finally {
    isInsertingImage.value = false;
  }
}

function handleDocumentReady() {
  // 这里只做最小日志输出；真实项目里通常会在这里打埋点或更新业务状态。
  console.log("ONLYOFFICE 文档已加载完成");
}

function handleLoadComponentError(errorCode, errorDescription) {
  // 当编辑器脚本地址不通、JWT 不匹配或 ONLYOFFICE 服务异常时，官方组件会走这里。
  errorMessage.value = `ONLYOFFICE 组件加载失败（${errorCode}）：${errorDescription}`;
}

// 页面首次挂载后立即请求配置，用户打开页面就能看到编辑器。
onMounted(loadEditorConfig);
</script>

<template>
  <main class="page-shell">
    <section v-if="isLoading" class="state-card">
      <p>正在获取编辑器配置...</p>
    </section>

    <section v-else-if="errorMessage" class="state-card error">
      <p>{{ errorMessage }}</p>
      <p class="hint">
        请确认 Spring Boot 已启动在 <code>{{ apiBaseUrl }}</code>，并且
        <code>http://localhost:8088/</code> 可以访问。
      </p>
    </section>

    <section v-else-if="editorPayload" class="editor-shell">
      <!--
        这里不再二次拼装配置，直接把后端返回的内容交给官方组件。
        这样能把 ONLYOFFICE 相关协议细节尽量收敛在后端。
      -->
      <DocumentEditor
        :key="editorKey"
        id="docEditor"
        :documentServerUrl="editorPayload.documentServerUrl"
        :config="editorPayload.config"
        height="100%"
        width="100%"
        :events_onDocumentReady="handleDocumentReady"
        :onLoadComponentError="handleLoadComponentError"
      />
    </section>

    <button class="panel-toggle" type="button" @click="togglePanel">
      {{ isPanelOpen ? "收起控制台" : "打开控制台" }}
    </button>

    <transition name="panel-fade">
      <button
        v-if="isPanelOpen"
        class="panel-backdrop"
        type="button"
        aria-label="关闭控制台"
        @click="closePanel"
      />
    </transition>

    <aside class="side-panel" :class="{ open: isPanelOpen }" aria-label="编辑器控制台">
      <div class="side-panel-header">
        <div class="hero-copy">
          <p class="eyebrow">Spring Boot + Vue + ONLYOFFICE</p>
          <h1>最小可运行集成</h1>
          <p class="summary">
            当前页面会向 Spring Boot 请求编辑配置，再把它交给 ONLYOFFICE Vue 组件。默认编辑的是
            <code>{{ currentDocumentTitle }}</code>，当前模式为
            <code>{{ readonly ? "只读" : "可编辑" }}</code>。
          </p>
        </div>
        <button class="panel-close" type="button" @click="closePanel">
          关闭
        </button>
      </div>

      <div class="hero-actions drawer-actions">
        <section class="panel-section">
          <p class="panel-section-title">当前文档</p>
          <p class="panel-document-title">{{ currentDocumentTitle }}</p>
          <p class="panel-document-meta">documentId: <code>{{ currentDocumentId }}</code></p>
        </section>

        <section class="panel-section">
          <p class="panel-section-title">导入文档</p>
          <input
            ref="fileInputRef"
            class="hidden-file-input"
            type="file"
            accept=".doc,.docx,.odt,.rtf,.txt,.xls,.xlsx,.ods,.csv,.ppt,.pptx,.odp,.pdf"
            @change="handleFileSelected"
          />
          <button
            class="ghost-button"
            type="button"
            :disabled="isLoading || isImportingDocument"
            @click="openFilePicker"
          >
            {{ isImportingDocument ? "处理中..." : "上传本地文档" }}
          </button>
          <label class="image-url-field">
            <span>网络文档地址</span>
            <input
              v-model="remoteDocumentUrl"
              class="image-url-input"
              type="url"
              placeholder="https://example.com/demo.docx"
              :disabled="isLoading || isImportingDocument"
            />
          </label>
          <button
            class="ghost-button secondary"
            type="button"
            :disabled="isLoading || isImportingDocument || !remoteDocumentUrl"
            @click="importRemoteDocument"
          >
            {{ isImportingDocument ? "处理中..." : "加载网络文档" }}
          </button>
        </section>

        <section class="panel-section">
          <p class="panel-section-title">编辑动作</p>
          <label class="image-url-field">
            <span>网络图片地址</span>
            <input
              v-model="imageUrl"
              class="image-url-input"
              type="url"
              placeholder="https://example.com/demo.png"
              :disabled="isLoading || isInsertingImage"
            />
          </label>
          <button
            class="ghost-button accent"
            type="button"
            :disabled="isLoading || isInsertingImage || readonly"
            @click="insertRemoteImage"
          >
            {{ isInsertingImage ? "插入中..." : "在光标处插入网络图片" }}
          </button>
          <button class="ghost-button" type="button" :disabled="isLoading" @click="toggleReadonly">
            {{ readonly ? "切换为可编辑" : "切换为只读" }}
          </button>
          <button class="ghost-button secondary" type="button" :disabled="isLoading" @click="loadEditorConfig">
            重新加载配置
          </button>
        </section>
      </div>
    </aside>
  </main>
</template>
