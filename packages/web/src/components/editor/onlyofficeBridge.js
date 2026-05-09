// 宿主页和 ONLYOFFICE 隐藏插件之间约定的消息通道。
// 所有 postMessage 都必须带上这个 channel，避免和页面里的其他 message 事件串线。
const BRIDGE_CHANNEL = "onlyoffice-ai-bridge";

// 宿主页和插件之间的消息类型定义。
// 这里统一收口，避免模板和运行时分别手写字符串造成拼写漂移。
export const ONLYOFFICE_AI_BRIDGE_EVENTS = {
  ready: "onlyoffice-ai-bridge:ready",
  error: "onlyoffice-ai-bridge:error",
  captureSelection: "onlyoffice-ai-bridge:capture-selection",
  selectionCaptured: "onlyoffice-ai-bridge:selection-captured",
  refreshOutline: "onlyoffice-ai-bridge:refresh-outline",
  outlineRefreshed: "onlyoffice-ai-bridge:outline-refreshed",
  jumpToHeading: "onlyoffice-ai-bridge:jump-to-heading",
  headingJumped: "onlyoffice-ai-bridge:heading-jumped",
  locateText: "onlyoffice-ai-bridge:locate-text",
  textLocated: "onlyoffice-ai-bridge:text-located",
  insertHtml: "onlyoffice-ai-bridge:insert-html",
  htmlInserted: "onlyoffice-ai-bridge:html-inserted"
};

/**
 * 创建统一的桥接错误对象，便于 UI 直接展示中文提示。
 *
 * @param {string} message - 具体错误文案。
 * @returns {Error} 标准 Error 实例。
 */
function createBridgeError(message) {
  return new Error(message || "文档桥接暂不可用，请稍后重试。");
}

/**
 * 创建宿主页到 ONLYOFFICE 隐藏插件的请求-响应桥。
 *
 * @param {object} options - 桥接依赖。
 * @param {() => object | null} options.getEditor - 获取 ONLYOFFICE editor 实例。
 * @param {() => HTMLIFrameElement | null} options.getIframe - 获取编辑器 iframe。
 * @param {number} [options.requestTimeoutMs=10000] - 单次桥接请求超时时间。
 * @returns {object} 暴露给 EditorShell 的桥接控制器。
 */
export function createOnlyofficeBridge({
  getEditor,
  getIframe,
  requestTimeoutMs = 10000
}) {
  // disposed / readyPayload / pluginWindow 是这座桥的三块核心运行态：
  // - disposed: 当前桥对象是否已被宿主页销毁；
  // - readyPayload: 插件是否已经完成握手；
  // - pluginWindow: 真正应该接收后续命令的插件 iframe 窗口。
  let disposed = false;
  let readyPayload = null;
  let pluginWindow = null;
  let readyPromise = null;
  let readyResolver = null;
  let readyRejecter = null;
  let readyTimeoutId = null;
  const pendingRequests = new Map();

  let capability = "plugin";
  try {
    // Developer 版支持 connector 能力时，先探测一下。
    // 但当前项目主链路仍以插件桥接为准，所以这里只做能力感知，不把 connector 当成硬依赖。
    const editor = getEditor?.();
    if (editor && typeof editor.createConnector === "function") {
      editor.createConnector();
      capability = "connector";
    }
  } catch {
    capability = "plugin";
  }

  function clearReadyTimeout() {
    if (readyTimeoutId !== null) {
      window.clearTimeout(readyTimeoutId);
      readyTimeoutId = null;
    }
  }

  function rejectPendingRequests(error) {
    // 页面切换、编辑器重载或桥接销毁时，要把所有在飞请求一次性回收，
    // 避免界面卡在 loading，或旧请求结果误写入新文档的 UI 状态。
    pendingRequests.forEach(({ reject, timeoutId }) => {
      window.clearTimeout(timeoutId);
      reject(error);
    });
    pendingRequests.clear();
  }

  function handleMessage(event) {
    const message = event.data;
    if (!message || message.channel !== BRIDGE_CHANNEL || typeof message.type !== "string") {
      return;
    }

    if (event.source) {
      // ready 事件来自插件真正运行的窗口层级。
      // 记住这个 source，后续所有请求都直接发给它，避免消息停在中间 iframe。
      pluginWindow = event.source;
    }

    if (message.type === ONLYOFFICE_AI_BRIDGE_EVENTS.ready) {
      clearReadyTimeout();
      readyPayload = {
        capability: message.capability || capability
      };
      capability = readyPayload.capability;
      readyResolver?.(readyPayload);
      readyResolver = null;
      readyRejecter = null;
      readyPromise = Promise.resolve(readyPayload);
      return;
    }

    if (!message.requestId || !pendingRequests.has(message.requestId)) {
      return;
    }

    const pending = pendingRequests.get(message.requestId);
    pendingRequests.delete(message.requestId);
    window.clearTimeout(pending.timeoutId);

    if (message.type === ONLYOFFICE_AI_BRIDGE_EVENTS.error) {
      pending.reject(createBridgeError(message.message || `${pending.label}失败。`));
      return;
    }

    pending.resolve(message.payload ?? {});
  }

  window.addEventListener("message", handleMessage);

  function waitForReady() {
    // waitForReady 是所有桥接调用的统一门闩：
    // 没 ready 时只保留一个 promise，让“抓选区 / 刷目录 / 跳章节”复用同一轮握手等待。
    if (disposed) {
      return Promise.reject(createBridgeError("文档桥接已销毁，请重新加载编辑器。"));
    }
    if (readyPayload) {
      return Promise.resolve(readyPayload);
    }
    if (readyPromise) {
      return readyPromise;
    }

    readyPromise = new Promise((resolve, reject) => {
      readyResolver = resolve;
      readyRejecter = reject;
      readyTimeoutId = window.setTimeout(() => {
        readyPromise = null;
        readyResolver = null;
        readyRejecter = null;
        reject(createBridgeError("文档桥接尚未就绪，请等待编辑器完全加载后重试。"));
      }, requestTimeoutMs);
    });

    return readyPromise;
  }

  function sendRequest(type, payload, label) {
    if (disposed) {
      return Promise.reject(createBridgeError("文档桥接已销毁，请重新加载编辑器。"));
    }

    // ready 之后优先给插件窗口发命令；
    // 还没 ready 时兜底使用编辑器 iframe，至少给“等待中”阶段留一条探活通路。
    const iframeWindow = pluginWindow || getIframe?.()?.contentWindow;
    if (!iframeWindow) {
      return Promise.reject(createBridgeError("编辑器 iframe 尚未准备完成，请稍后再试。"));
    }

    // requestId 是宿主页和插件一一对应请求-响应的关键。
    // 选区抓取、目录刷新、章节跳转都通过它做匹配，避免并发时串响应。
    const requestId = `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`;

    return new Promise((resolve, reject) => {
      const timeoutId = window.setTimeout(() => {
        pendingRequests.delete(requestId);
        reject(createBridgeError(`${label}超时，请稍后重试。`));
      }, requestTimeoutMs);

      pendingRequests.set(requestId, {
        label,
        resolve,
        reject,
        timeoutId
      });

      iframeWindow.postMessage(
        {
          channel: BRIDGE_CHANNEL,
          type,
          requestId,
          payload: payload ?? {}
        },
        "*"
      );
    });
  }

  return {
    get capability() {
      return capability;
    },
    waitForReady,
    async captureSelectedText() {
      // 先等桥接 ready，再请求插件执行 GetSelectedText。
      await waitForReady();
      return sendRequest(ONLYOFFICE_AI_BRIDGE_EVENTS.captureSelection, {}, "抓取当前选区");
    },
    async refreshOutline() {
      // 插件内部会遍历段落并提取 Heading 样式，宿主页只关心结构化结果。
      await waitForReady();
      return sendRequest(ONLYOFFICE_AI_BRIDGE_EVENTS.refreshOutline, {}, "刷新章节目录");
    },
    async jumpToHeading(heading) {
      // 宿主页只传稳定定位信息，不直接操作编辑器 DOM。
      // 真实跳转动作由插件在编辑器内部执行，避免跨 iframe 的不稳定选择行为。
      await waitForReady();
      return sendRequest(
        ONLYOFFICE_AI_BRIDGE_EVENTS.jumpToHeading,
        {
          id: heading?.id ?? "",
          paragraphIndex: heading?.paragraphIndex ?? -1
        },
        "定位章节标题"
      );
    },
    async selectTextOnPage(target) {
      // 指定页码 + 文本的临时选中必须在插件内部执行，避免宿主页跨 iframe 操作编辑器 DOM。
      await waitForReady();
      return sendRequest(
        ONLYOFFICE_AI_BRIDGE_EVENTS.locateText,
        {
          pageIndex: Number.isInteger(target?.pageIndex) ? target.pageIndex : -1,
          text: typeof target?.text === "string" ? target.text : "",
          occurrence: Number.isInteger(target?.occurrence) ? target.occurrence : 0,
          matchCase: Boolean(target?.matchCase)
        },
        "定位并选中文本"
      );
    },
    async insertHtml(html) {
      // 写入动作统一交给插件端 PasteHtml，宿主页只负责请求-响应配对和超时控制。
      await waitForReady();
      return sendRequest(
        ONLYOFFICE_AI_BRIDGE_EVENTS.insertHtml,
        { html: typeof html === "string" ? html : "" },
        "写入 HTML 到文档"
      );
    },
    dispose() {
      if (disposed) {
        return;
      }
      disposed = true;
      // 销毁时把监听和在飞请求都彻底释放干净，防止文档切换后旧桥继续接消息。
      clearReadyTimeout();
      window.removeEventListener("message", handleMessage);
      rejectPendingRequests(createBridgeError("文档桥接已销毁，请重新加载编辑器。"));
      readyRejecter?.(createBridgeError("文档桥接已销毁，请重新加载编辑器。"));
      readyPromise = null;
      readyResolver = null;
      readyRejecter = null;
      readyPayload = null;
      pluginWindow = null;
    }
  };
}
