<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { DArrowLeft, DArrowRight } from "@element-plus/icons-vue";
import { DocumentEditor } from "@onlyoffice/document-editor-vue";
import { apiFetch, buildApiUrl, createAccessContextHeaders, parseJsonEnvelope } from "../../lib/api";
import EditorAiWorkbench from "./EditorAiWorkbench.vue";
import { createOnlyofficeBridge } from "./onlyofficeBridge";
import { startRuntimeEventStream } from "./runtimeEventStream";
import { useWriteBackStore } from "../../stores/writeBackStore.js";

const props = defineProps({
  documentId: {
    type: String,
    required: true
  },
  documentTitle: {
    type: String,
    default: ""
  },
  readonly: {
    type: Boolean,
    default: false
  },
  showConsole: {
    type: Boolean,
    default: true
  }
});

// 编辑器壳层集中维护三类状态：
// 1. 编辑器加载与会话生命周期；
// 2. ONLYOFFICE 桥接能力状态；
// 3. 右侧 AI-ready 抽屉面板的展示状态。
const isConsoleOpen = ref(false);
const isLoading = ref(true);
const isInsertingImage = ref(false);
const errorMessage = ref("");
const editorPayload = ref(null);
const editorKey = ref(0);
const saveStatus = ref(null);
const editingSessionOpened = ref(false);
const isClosingSession = ref(false);
const selectedText = ref("");
const isCapturingSelection = ref(false);
const hasEmptySelection = ref(false);
const outlineItems = ref([]);
const isRefreshingOutline = ref(false);
const hasEmptyOutline = ref(false);
const bridgeErrorMessage = ref("");
const bridgeStatusMessage = ref("等待文档运行态桥接就绪。");
const bridgeReady = ref(false);
const bridgeCapability = ref("plugin");
const activeHeadingId = ref("");
const activeHeadingNode = ref(null);
const writeBackStore = useWriteBackStore();
let saveStatusTimer = null;
let closeEditingSessionPromise = null;
let removeUnloadListeners = null;
let onlyofficeBridge = null;
let runtimeStreamHandle = null;
let runtimeStreamRetryTimer = null;
let runtimeStreamRetryDelayMs = 1000;
const isRuntimeStreamHealthy = ref(false);

// modeLabel / shouldShowConsole / bridgeStatusType 都是给模板直接消费的视图衍生态，
// 保证模板层不再额外拼判断，便于后面继续往“AI 对话正式版”演进。
const modeLabel = computed(() => (props.readonly ? "预览模式" : "编辑模式"));
const shouldShowConsole = computed(() => props.showConsole && !props.readonly);
const bridgeStatusType = computed(() => {
  if (bridgeErrorMessage.value) {
    return "danger";
  }
  return bridgeReady.value ? "success" : "info";
});

const outlineTreeData = computed(() => {
  // 后端和插件返回的是扁平 heading 数组，这里按 level 还原成树结构，
  // 交给 Element Plus Tree 后就能直接得到“章节目录”交互。
  const tree = [];
  const stack = [];
  for (const item of outlineItems.value) {
    const node = { ...item, id: item.id, label: item.numberingPrefix ? item.numberingPrefix + " " + (item.text || "未命名标题") : (item.text || "未命名标题"), level: item.level, children: [] };
    while (stack.length > 0 && stack[stack.length - 1].level >= node.level) {
      stack.pop();
    }
    if (stack.length > 0) {
      stack[stack.length - 1].children.push(node);
    } else {
      tree.push(node);
    }
    stack.push(node);
  }
  return tree;
});
const bridgeStatusLabel = computed(() => {
  if (bridgeErrorMessage.value) {
    return "桥接异常";
  }
  return bridgeReady.value ? "桥接已就绪" : "桥接连接中";
});
const bridgeCapabilityLabel = computed(() => (bridgeCapability.value === "connector" ? "connector + plugin" : "plugin"));
const runtimeContext = computed(() => ({
  documentId: props.documentId,
  documentTitle: props.documentTitle,
  selectedText: selectedText.value,
  hasEmptySelection: hasEmptySelection.value,
  outlineTreeData: outlineTreeData.value,
  activeHeadingId: activeHeadingId.value,
  activeHeadingNode: activeHeadingNode.value,
  bridgeStatusMessage: bridgeStatusMessage.value,
  bridgeReady: bridgeReady.value,
  saveStatus: saveStatus.value
}));

/**
 * 从失败响应中读取后端统一错误文案。
 *
 * @param {Response} response - fetch 返回的失败响应。
 * @param {string} fallbackMessage - 响应体不可解析时的兜底文案。
 * @returns {Promise<string>} 可展示给用户的错误信息。
 */
async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload?.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

function getDocEditorInstance() {
  // ONLYOFFICE Vue 组件会把实例挂到 window.DocEditor.instances.docEditor。
  // 这里统一封装，避免模板和业务函数到处直接读全局变量。
  return window.DocEditor?.instances?.docEditor;
}

function getDocEditorIframe() {
  // 宿主页需要拿 iframe 的原因主要有两个：
  // 1. 桥接初始化时兜底定位编辑器窗口；
  // 2. 编辑器 ready 后尝试自动打开左侧导航面板。
  return document.getElementById("docEditor")?.querySelector("iframe") ?? null;
}

function resetBridgeState() {
  // 每次文档切换、编辑器重载或桥接销毁时，都回到同一套“未连接”初始态，
  // 避免上一份文档的选区/目录残留在当前抽屉里。
  selectedText.value = "";
  hasEmptySelection.value = false;
  outlineItems.value = [];
  hasEmptyOutline.value = false;
  bridgeErrorMessage.value = "";
  bridgeStatusMessage.value = "等待文档运行态桥接就绪。";
  bridgeReady.value = false;
  bridgeCapability.value = "plugin";
  activeHeadingId.value = "";
  activeHeadingNode.value = null;
}

/**
 * 销毁当前 ONLYOFFICE 插件桥并重置桥接相关 UI 状态。
 */
function disposeBridge() {
  onlyofficeBridge?.dispose();
  onlyofficeBridge = null;
  resetBridgeState();
}

/**
 * 懒创建 ONLYOFFICE 桥接实例。
 *
 * @returns {ReturnType<typeof createOnlyofficeBridge> | null} 可用桥接实例；编辑器配置未加载时返回 null。
 */
function ensureBridge() {
  if (!editorPayload.value) {
    return null;
  }
  if (!onlyofficeBridge) {
    // 桥接对象按需创建，并绑定当前编辑器实例与 iframe 获取器。
    // 这样在编辑器重新 mount 后，不需要页面层手动维护窗口引用。
    onlyofficeBridge = createOnlyofficeBridge({
      getEditor: getDocEditorInstance,
      getIframe: getDocEditorIframe
    });
    bridgeCapability.value = onlyofficeBridge.capability;
  }
  return onlyofficeBridge;
}

function toBridgeErrorMessage(error, fallbackMessage) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallbackMessage;
}

/**
 * 等待隐藏插件完成 ready 握手。
 *
 * @param {{suppressErrors?: boolean}} options - 是否静默处理握手失败。
 * @returns {Promise<boolean>} true 表示后续可安全发起选区/目录/写回请求。
 */
async function waitForBridgeReady(options = {}) {
  const { suppressErrors = false } = options;
  const bridge = ensureBridge();
  if (!bridge) {
    return false;
  }
  if (bridgeReady.value) {
    return true;
  }

  bridgeStatusMessage.value = "正在连接文档运行态桥接...";
  bridgeErrorMessage.value = "";

  try {
    // ready 阶段本质上是在等隐藏插件向宿主页发送握手消息。
    const payload = await bridge.waitForReady();
    bridgeReady.value = true;
    bridgeCapability.value = payload?.capability || bridge.capability;
    bridgeStatusMessage.value = "文档桥接已就绪，可读取选中文本并刷新章节目录。";
    return true;
  } catch (error) {
    bridgeReady.value = false;
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "文档桥接暂不可用，请稍后重试。");
    bridgeStatusMessage.value = "文档桥接未能成功建立。";
    if (!suppressErrors) {
      console.error("文档桥接初始化失败", error);
    }
    return false;
  }
}

/**
 * 通过桥接插件读取当前 ONLYOFFICE 选区。
 *
 * @returns {Promise<object | null>} 插件返回的选区快照；桥接不可用时返回 null。
 */
async function captureSelectedText() {
  if (!(await waitForBridgeReady())) {
    return null;
  }

  // 选区抓取是未来 AI 对话“带上下文提问”的前置能力，
  // 所以这里除了拿文本本身，还要明确告诉用户当前是否为空选区。
  isCapturingSelection.value = true;
  bridgeErrorMessage.value = "";

  try {
    const payload = await onlyofficeBridge.captureSelectedText();
    selectedText.value = payload.text ?? "";
    hasEmptySelection.value = Boolean(payload.emptySelection || selectedText.value.trim().length === 0);
    bridgeStatusMessage.value = hasEmptySelection.value
      ? "当前没有选中文本，可先在文档中框选一段内容。"
      : "已获取当前选中文本，可作为下一阶段 AI 对话的上下文输入。";
    return payload;
  } catch (error) {
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "获取当前选中文本失败，请稍后重试。");
    return null;
  } finally {
    isCapturingSelection.value = false;
  }
}

async function refreshOutline(options = {}) {
  const { silent = false } = options;
  if (!(await waitForBridgeReady({ suppressErrors: silent }))) {
    return null;
  }

  isRefreshingOutline.value = true;
  if (!silent) {
    bridgeErrorMessage.value = "";
  }

  try {
    // 章节目录来自插件内部遍历文档段落的结果。
    // 宿主页只负责把结果渲染成树，并提供点击跳转。
    const payload = await onlyofficeBridge.refreshOutline();
    outlineItems.value = Array.isArray(payload.headings) ? payload.headings : [];
    hasEmptyOutline.value = Boolean(payload.emptyOutline || outlineItems.value.length === 0);
    bridgeStatusMessage.value = hasEmptyOutline.value
      ? "当前文档没有检测到标题段落。"
      : "章节目录已刷新，可点击标题快速定位。";
    return payload;
  } catch (error) {
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "刷新章节目录失败，请稍后重试。");
    if (!silent) {
      outlineItems.value = [];
      hasEmptyOutline.value = false;
    }
    return null;
  } finally {
    isRefreshingOutline.value = false;
  }
}

async function jumpToHeading(heading) {
  if (!heading || !(await waitForBridgeReady())) {
    return null;
  }

  bridgeErrorMessage.value = "";

  try {
    // 跳转成功后同时更新抽屉顶部的“当前活跃标题”提示，
    // 让用户知道自己已经被定位到哪一章。
    const payload = await onlyofficeBridge.jumpToHeading(heading);
    activeHeadingId.value = heading.id;
    activeHeadingNode.value = heading;
    bridgeStatusMessage.value = `已定位到章节：${heading.text || heading.id}`;
    return payload;
  } catch (error) {
    bridgeErrorMessage.value = toBridgeErrorMessage(error, "章节定位失败，请刷新目录后重试。");
    return null;
  }
}

async function loadEditorConfig() {
  isLoading.value = true;
  errorMessage.value = "";
  disposeBridge();

  try {
    // editor-config 由后端统一签发：
    // - 同源的 ONLYOFFICE 文档服务地址
    // - 文档 key/token/config
    // - 编辑态下自动挂载的隐藏桥接插件配置
    const response = await apiFetch("/api/documents/get/editor-config", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentId: props.documentId, readonly: props.readonly })
    });
    editorPayload.value = await parseJsonEnvelope(response);
    editingSessionOpened.value = !props.readonly;
    editorKey.value += 1;
    ensureBridge();
    saveStatus.value = null;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "未知错误";
    if (!props.readonly) {
      await closeEditingSession({ suppressErrors: true, force: true });
    }
  } finally {
    isLoading.value = false;
  }
}

async function fetchSaveStatusSnapshot(options = {}) {
  const { suppressErrors = false } = options;

  try {
    // 保存状态来自我们自己的后端，不依赖 ONLYOFFICE iframe DOM。
    const response = await apiFetch("/api/documents/get/save-status", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentId: props.documentId })
    });
    return await parseJsonEnvelope(response);
  } catch (error) {
    if (!suppressErrors) {
      throw error;
    }
    return null;
  }
}

async function loadSaveStatus() {
  if (props.readonly) {
    return;
  }

  try {
    const payload = await fetchSaveStatusSnapshot();
    if (payload) {
      saveStatus.value = payload;
    }
  } catch (error) {
    console.error("加载保存状态失败", error);
  }
}

function toggleConsole() {
  // 右侧工作台默认收起，避免首次进入编辑页时压缩编辑区域。
  isConsoleOpen.value = !isConsoleOpen.value;
}

function closeConsole() {
  isConsoleOpen.value = false;
}

async function insertRemoteImage(sourceUrl) {
  if (props.readonly) {
    errorMessage.value = "预览模式下不能插入图片。";
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
    // 图片插入仍沿用现有后端接口：
    // 后端生成 ONLYOFFICE insertImage 所需配置，前端只负责调用 editor 实例写入。
    const response = await apiFetch(`/api/documents/${props.documentId}/images/insert`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        sourceUrl
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

async function handleInsertHtml({ html }) {
  // Bridge 实例存在且握手完成后才允许写回文档。
  if (!onlyofficeBridge || !bridgeReady.value) {
    writeBackStore.status = "error";
    writeBackStore.errorMsg = "文档连接未就绪，请等待编辑器完全加载后重试。";
    return;
  }

  try {
    await onlyofficeBridge.insertHtml(html);
    writeBackStore.status = "success";
  } catch (error) {
    const msg = error instanceof Error ? error.message : "写入文档失败，请重试。";
    writeBackStore.status = "error";
    writeBackStore.errorMsg = msg;
  }
}

function handleDocumentReady() {
  // onDocumentReady 说明编辑器 iframe 已完成主加载。
  // 之后才能安全启动保存轮询、桥接握手和导航面板展开。
  if (props.readonly) {
    stopRuntimeEventStream();
    clearRuntimeStreamRetry();
  } else {
    activateRuntimeStream();
  }
  void nextTick(async () => {
    ensureBridge();
    const ready = await waitForBridgeReady({ suppressErrors: true });
    if (ready) {
      await refreshOutline({ silent: true });
    }
  });
  openNavigationPanelAfterReady();
}

function openNavigationPanelAfterReady() {
  setTimeout(() => {
    try {
      // 这里是一个“同源时增强”的小优化：
      // 如果 iframe 同源，就尝试自动点开左侧导航，帮助用户更快看到文档目录。
      const iframe = getDocEditorIframe();
      if (!iframe) {
        return;
      }

      const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
      if (!iframeDoc) {
        return;
      }

      const navBtn = iframeDoc.getElementById("left-btn-navigation");
      if (navBtn && !navBtn.classList.contains("active") && !navBtn.classList.contains("pressed")) {
        navBtn.click();
      }
    } catch {
      // 同源判断失败或按钮不存在时静默降级，不影响编辑器正常使用
    }
  }, 800);
}

function handleLoadComponentError(errorCode, errorDescription) {
  // 组件级错误通常是 DocsAPI、反向代理或文档服务地址不可达。
  disposeBridge();
  errorMessage.value = `ONLYOFFICE 组件加载失败（${errorCode}）：${errorDescription}`;
}

function startSaveStatusPolling() {
  stopSaveStatusPolling();
  saveStatusTimer = window.setInterval(() => {
    loadSaveStatus();
  }, 5000);
}

function stopSaveStatusPolling() {
  if (saveStatusTimer !== null) {
    window.clearInterval(saveStatusTimer);
    saveStatusTimer = null;
  }
}

function clearRuntimeStreamRetry() {
  if (runtimeStreamRetryTimer !== null) {
    window.clearTimeout(runtimeStreamRetryTimer);
    runtimeStreamRetryTimer = null;
  }
}

function stopRuntimeEventStream() {
  // 这里故意先“逻辑断开”，再“物理 abort”：
  // 1. 先把 runtimeStreamHandle 置空，声明当前页面不再认这个流；
  // 2. 再去 abort 旧连接。
  // 这样旧流即使稍后异步触发 onError/onComplete，也会因为 handle 不匹配而被 guard 掉，
  // 不会把旧文档或旧状态的失败事件反向污染当前页面。
  const handle = runtimeStreamHandle;
  runtimeStreamHandle = null;
  isRuntimeStreamHealthy.value = false;
  void handle?.abort?.();
}

function isRuntimeStreamEligibleFor(documentId) {
  // SSE 是编辑运行态的唯一活跃性通道，即使工作台被隐藏也要保持。
  // 这里三个条件缺一不可：
  // - 不是 readonly；
  // - editing session 还开着；
  // - 流对应的 documentId 仍然是当前页面这份文档。
  return !props.readonly
    && editingSessionOpened.value
    && documentId === props.documentId;
}

function scheduleRuntimeStreamRetry(documentId) {
  if (!isRuntimeStreamEligibleFor(documentId)) {
    clearRuntimeStreamRetry();
    return;
  }

  // 每次进入这里都先清空旧 timer，保证同一时刻只有一个重连时钟。
  // 这样即使 `runtime-error`、reader error、ready reject 连续到来，
  // 最后也只会留下一个有效的 retry 任务。
  clearRuntimeStreamRetry();
  const delay = runtimeStreamRetryDelayMs;
  runtimeStreamRetryTimer = window.setTimeout(() => {
    runtimeStreamRetryTimer = null;
    if (!isRuntimeStreamEligibleFor(documentId)) {
      return;
    }
    runtimeStreamRetryDelayMs = Math.min(delay * 2, 15000);
    activateRuntimeStream(documentId);
  }, delay);
}

function activateRuntimePollingFallback(documentId) {
  // fallback 只恢复保存状态查询和 SSE 重连。
  // editing session 活跃性不再走独立 REST heartbeat，避免浏览器轮询和 SSE 双通道并存。
  if (props.readonly || documentId !== props.documentId || !editingSessionOpened.value) {
    stopSaveStatusPolling();
    clearRuntimeStreamRetry();
    return;
  }

  void loadSaveStatus();
  startSaveStatusPolling();
  if (isRuntimeStreamEligibleFor(documentId)) {
    scheduleRuntimeStreamRetry(documentId);
  } else {
    clearRuntimeStreamRetry();
  }
}

function updateSaveStatusFromRuntime(payload, streamDocumentId) {
  if (!payload || typeof payload !== "object") {
    return;
  }
  if (payload.documentId && payload.documentId !== streamDocumentId) {
    return;
  }
  if (payload.documentId && payload.documentId !== props.documentId) {
    return;
  }
  saveStatus.value = payload;
}

function handleRuntimeStreamFailure(streamDocumentId) {
  // 失败并不总意味着“当前页面这条流坏了”。
  // 也可能只是：
  // - 旧文档的流在切页后晚到一个错误；
  // - 旧 handle 在 stopRuntimeEventStream 之后又回调了一次；
  // - 页面已经切成 readonly 或 session 已关闭。
  // 这些都不应该再驱动当前页面进入 fallback。
  if (runtimeStreamHandle === null && streamDocumentId !== props.documentId) {
    return;
  }
  isRuntimeStreamHealthy.value = false;
  if (streamDocumentId !== props.documentId) {
    return;
  }
  activateRuntimePollingFallback(streamDocumentId);
}

function startRuntimeEventStreamForDocument(documentId) {
  // 这里把“建流”和“页面状态机切换”绑在一起。
  //
  // 正常路径：
  // 1. startRuntimeEventStream 建出 handle；
  // 2. ready resolve；
  // 3. 页面把 save-status 与 editing-session liveness 主通道切到 SSE；
  // 4. 后续 save-status 事件不断覆盖本地状态。
  //
  // 异常路径：
  // 1. runtime-error / onError 进入 fallback；
  // 2. fallback 恢复 save-status polling；
  // 3. retry timer 按退避策略重连，liveness 仍由重建后的 SSE 承担。
  //
  // clean completion 路径：
  // 1. 不进入 fallback；
  // 2. 直接尝试重新 activateRuntimeStream；
  // 3. 避免服务端正常超时关闭流时，前端短暂恢复旧轮询造成状态抖动。
  const streamHandle = startRuntimeEventStream({
    documentId,
    onSaveStatus(payload) {
      if (runtimeStreamHandle !== streamHandle) {
        return;
      }
      updateSaveStatusFromRuntime(payload, documentId);
    },
    onSessionActive() {},
    onKeepalive() {},
    onRuntimeError() {
      if (runtimeStreamHandle !== streamHandle) {
        return;
      }
      handleRuntimeStreamFailure(documentId);
    },
    onError() {
      if (runtimeStreamHandle !== streamHandle) {
        return;
      }
      handleRuntimeStreamFailure(documentId);
    },
    onComplete() {
      if (runtimeStreamHandle !== streamHandle) {
        return;
      }
      runtimeStreamHandle = null;
      isRuntimeStreamHealthy.value = false;
      clearRuntimeStreamRetry();
      if (!isRuntimeStreamEligibleFor(documentId)) {
        return;
      }
      void Promise.resolve().then(() => {
        if (isRuntimeStreamEligibleFor(documentId)) {
          activateRuntimeStream(documentId);
        }
      });
    }
  });

  runtimeStreamHandle = streamHandle;
  streamHandle.ready
    .then(() => {
      if (runtimeStreamHandle !== streamHandle || !isRuntimeStreamEligibleFor(documentId)) {
        return;
      }
      clearRuntimeStreamRetry();
      runtimeStreamRetryDelayMs = 1000;
      isRuntimeStreamHealthy.value = true;
      stopSaveStatusPolling();
      // save-status 和 editing-session liveness 主通道都在 runtime-events 上。
    })
    .catch(() => {
      if (runtimeStreamHandle !== streamHandle) {
        return;
      }
      handleRuntimeStreamFailure(documentId);
    });
}

function activateRuntimeStream(documentId = props.documentId) {
  // activateRuntimeStream 是“切主通道”的动作，不是简单 start：
  // 1. 先停掉旧 save-status polling；
  // 2. 清空旧 retry timer；
  // 3. abort 旧 stream；
  // 4. 最后再为当前 documentId 建一条新的 SSE 流。
  // 这样能保证页面任意时刻只认一条主链路，不会出现 SSE 和轮询同时写状态。
  if (!isRuntimeStreamEligibleFor(documentId)) {
    stopRuntimeEventStream();
    clearRuntimeStreamRetry();
    return;
  }

  stopSaveStatusPolling();
  clearRuntimeStreamRetry();
  stopRuntimeEventStream();
  startRuntimeEventStreamForDocument(documentId);
}

function destroyDocEditor() {
  try {
    const editor = getDocEditorInstance();
    if (editor && typeof editor.destroyEditor === "function") {
      editor.destroyEditor();
    }
  } catch {
    // 编辑器实例可能已被清理，静默忽略
  }
}

async function closeEditingSession(options = {}) {
  const {
    keepalive = false,
    suppressErrors = false,
    force = false
  } = options;

  if (props.readonly) {
    return null;
  }
  if (!editingSessionOpened.value && !force) {
    return null;
  }
  if (closeEditingSessionPromise) {
    // 同一轮离开流程里复用已有 promise，确保 save/close 只发一次。
    try {
      return await closeEditingSessionPromise;
    } catch (error) {
      if (!suppressErrors) {
        throw error;
      }
      return null;
    }
  }

  stopRuntimeEventStream();
  clearRuntimeStreamRetry();
  stopSaveStatusPolling();

  isClosingSession.value = true;
  closeEditingSessionPromise = (async () => {
    if (!keepalive) {
      // 显式离开编辑页时先主动触发一次保存，再关闭 editing session，
      // 这样能把“离开即保存”的体验收口到一个稳定流程里。
      const saveResponse = await apiFetch("/api/documents/save", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ documentId: props.documentId })
      });
      saveStatus.value = await parseJsonEnvelope(saveResponse);
    }

    destroyDocEditor();

    // 编辑器销毁后再通知后端关闭会话，避免前端残留实例继续发送 callback。
    const response = await apiFetch("/api/documents/close/session", {
      method: "POST",
      keepalive,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentId: props.documentId })
    });
    const payload = await parseJsonEnvelope(response);
    editingSessionOpened.value = false;
    saveStatus.value = payload;
    return payload;
  })();

  try {
    return await closeEditingSessionPromise;
  } catch (error) {
    if (!suppressErrors) {
      throw error;
    }
    return null;
  } finally {
    isClosingSession.value = false;
    closeEditingSessionPromise = null;
  }
}

function dispatchUnloadCloseRequest() {
  if (props.readonly || !editingSessionOpened.value || closeEditingSessionPromise) {
    return;
  }

  // beforeunload/pagehide 场景下不能依赖复杂异步流程，
  // 这里只保留一个最小 keepalive close 请求，尽量把后端会话收尾掉。
  editingSessionOpened.value = false;
  stopRuntimeEventStream();
  clearRuntimeStreamRetry();
  stopSaveStatusPolling();

  fetch(buildApiUrl("/api/documents/close/session"), {
    method: "POST",
    keepalive: true,
    headers: createAccessContextHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ documentId: props.documentId })
  }).catch(() => {});
}

function formatTimestamp(value) {
  if (!value) {
    return "暂无";
  }

  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "medium"
  }).format(new Date(value));
}

function saveStatusTone(state) {
  return {
    "is-idle": state === "idle",
    "is-progress": state === "callback-received" || state === "editing",
    "is-success": state === "saved",
    "is-error": state === "save-failed" || state === "failed"
  };
}

watch(
  () => [props.documentId, props.readonly, props.showConsole],
  async () => {
    // 文档切换时必须按顺序重置桥接、轮询和保存状态，
    // 否则上一份文档的运行态很容易污染新文档页面。
    disposeBridge();
    stopRuntimeEventStream();
    clearRuntimeStreamRetry();
    stopSaveStatusPolling();
    saveStatus.value = null;
    isRuntimeStreamHealthy.value = false;
    runtimeStreamRetryDelayMs = 1000;
    editingSessionOpened.value = false;
    if (!props.showConsole || props.readonly) {
      isConsoleOpen.value = false;
    }
    await loadEditorConfig();
  },
  { immediate: true }
);

onMounted(() => {
  const handlePageHide = () => {
    dispatchUnloadCloseRequest();
  };
  // pagehide + beforeunload 两个事件都挂上，兼顾浏览器标签关闭和常规页面跳转。
  window.addEventListener("pagehide", handlePageHide);
  window.addEventListener("beforeunload", handlePageHide);
  removeUnloadListeners = () => {
    window.removeEventListener("pagehide", handlePageHide);
    window.removeEventListener("beforeunload", handlePageHide);
  };
});

onBeforeUnmount(async () => {
  disposeBridge();
  stopRuntimeEventStream();
  clearRuntimeStreamRetry();
  stopSaveStatusPolling();
  removeUnloadListeners?.();
  await closeEditingSession({ keepalive: true, suppressErrors: true });
});

defineExpose({
  // 页面层只暴露“离开前收尾”和“桥接能力入口”，避免父组件越过壳层直接摸内部状态。
  closeEditingSession,
  captureSelectedText,
  refreshOutline,
  jumpToHeading,
  outlineTreeData,
  isRefreshingOutline,
  hasEmptyOutline,
  activeHeadingId,
  // 暴露给外部调用：运行态 / 保存情况
  saveStatus,
  modeLabel,
  loadSaveStatus,
  loadEditorConfig,
  saveStatusTone,
  handleInsertHtml
});
</script>

<template>
  <el-container class="editor-workspace">
    <el-main class="editor-stage-stack">
      <el-empty key="loading" v-if="isLoading" description="正在获取编辑器配置..." />

      <el-alert key="error" v-else-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" style="margin: 16px;">
        <p class="hint">
          请确认当前站点的 <code>/api</code> 反向代理可用，并且 ONLYOFFICE 相关路径已通过同源方式转发。
        </p>
      </el-alert>

      <div key="editor" v-else-if="editorPayload" class="editor-shell">
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
      </div>

      <div
        v-if="shouldShowConsole"
        class="drawer-collapse-btn drawer-collapse-btn-right"
        :class="{ 'is-open': isConsoleOpen }"
        @click="toggleConsole"
        :title="isConsoleOpen ? '收起 AI 对话工作台' : '打开 AI 对话工作台'"
      >
        <el-icon>
          <DArrowRight v-if="isConsoleOpen" />
          <DArrowLeft v-else />
        </el-icon>
      </div>
    </el-main>

    <div v-show="shouldShowConsole && isConsoleOpen" class="floating-console">
      <div class="console-body">
        <EditorAiWorkbench
          :document-title="props.documentTitle"
          :runtime-context="runtimeContext"
          :loading="isLoading"
          :closing="isClosingSession"
          @capture-selection="captureSelectedText"
          @refresh-outline="refreshOutline"
          @jump-to-heading="jumpToHeading"
          @insert-image="insertRemoteImage"
          @insert-html="handleInsertHtml"
          @close="closeConsole"
        />
      </div>
    </div>
  </el-container>
</template>

<style scoped>
/* 编辑区和右侧 AI-ready 面板拆成双栏布局，左侧优先保证文档编辑空间。 */
.editor-workspace {
  display: flex;
  flex-direction: row;
  height: 100vh;
  min-height: 0;
  background: var(--el-bg-color-page);
}

.editor-stage-stack {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  padding: 0;
}

.editor-shell {
  flex: 1;
  min-height: 0;
}

.editor-shell > div {
  height: 100%;
}



.floating-console {
  width: 800px;
  max-width: 100vw;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color);
  display: flex;
  flex-direction: column;
}

@media (max-width: 1439px) {
  .floating-console {
    width: min(70vw, 800px);
  }
}

@media (max-width: 1023px) {
  .floating-console {
    width: 100vw;
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    z-index: 120;
  }
}

.eyebrow {
  font-size: 12px;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0;
}

.title {
  margin: 8px 0;
  font-size: 20px;
}

.summary {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin: 0;
}

.console-body {
  flex: 1;
  overflow: hidden;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.panel-section {
  --el-card-padding: 16px;
}

.panel-section-header,
.console-inline-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.console-inline-actions {
  margin: 16px 0;
}

.panel-document-title,
.panel-document-meta {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.panel-document-title {
  font-weight: bold;
}

.panel-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.panel-inline-alert {
  margin-top: 12px;
}

.bridge-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bridge-capability {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.selection-preview {
  /* 选区预览可能包含多段文本和换行，这里保留原始结构方便直接送给 AI。 */
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  max-height: 220px;
  overflow: auto;
}

.selection-preview pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.6;
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
  /* 目录里标题较长时省略显示，避免右侧面板因为超长标题被撑坏。 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.runtime-block + .runtime-block {
  margin-top: 20px;
}

.runtime-title {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.save-status-card {
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  margin-bottom: 12px;
}

.save-status-meta {
  margin: 4px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.save-status-events {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}

.save-status-events li {
  padding: 8px 12px;
  background: var(--el-fill-color);
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.save-status-events strong {
  color: var(--el-color-primary);
}

.save-status-events time {
  color: var(--el-text-color-secondary);
}


.drawer-collapse-btn {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 60px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  color: #1677ff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.drawer-collapse-btn:hover {
  background: #f4f8ff;
  border-color: #b7d7ff;
  color: #1677ff;
  width: 24px;
}
.drawer-collapse-btn:active {
  transform: translateY(-50%) scale(0.96);
}
.drawer-collapse-btn-right {
  right: 0;
  border-radius: 14px 0 0 14px;
  border-right: none;
}
.drawer-collapse-btn-right.is-open {
  right: 800px;
}
@media (max-width: 1439px) {
  .drawer-collapse-btn-right.is-open {
    right: 450px;
  }
}
@media (max-width: 900px) {
  .drawer-collapse-btn-right.is-open {
    right: 100vw;
  }
}
</style>
