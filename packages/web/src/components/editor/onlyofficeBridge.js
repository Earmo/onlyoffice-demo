const BRIDGE_CHANNEL = "onlyoffice-ai-bridge";

export const ONLYOFFICE_AI_BRIDGE_EVENTS = {
  ready: "onlyoffice-ai-bridge:ready",
  error: "onlyoffice-ai-bridge:error",
  captureSelection: "onlyoffice-ai-bridge:capture-selection",
  selectionCaptured: "onlyoffice-ai-bridge:selection-captured",
  refreshOutline: "onlyoffice-ai-bridge:refresh-outline",
  outlineRefreshed: "onlyoffice-ai-bridge:outline-refreshed",
  jumpToHeading: "onlyoffice-ai-bridge:jump-to-heading",
  headingJumped: "onlyoffice-ai-bridge:heading-jumped"
};

function createBridgeError(message) {
  return new Error(message || "文档桥接暂不可用，请稍后重试。");
}

export function createOnlyofficeBridge({
  getEditor,
  getIframe,
  requestTimeoutMs = 10000
}) {
  let disposed = false;
  let readyPayload = null;
  let readyPromise = null;
  let readyResolver = null;
  let readyRejecter = null;
  let readyTimeoutId = null;
  const pendingRequests = new Map();

  let capability = "plugin";
  try {
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

    const iframeWindow = getIframe?.()?.contentWindow;
    if (!iframeWindow) {
      return Promise.reject(createBridgeError("编辑器 iframe 尚未准备完成，请稍后再试。"));
    }

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
      await waitForReady();
      return sendRequest(ONLYOFFICE_AI_BRIDGE_EVENTS.captureSelection, {}, "抓取当前选区");
    },
    async refreshOutline() {
      await waitForReady();
      return sendRequest(ONLYOFFICE_AI_BRIDGE_EVENTS.refreshOutline, {}, "刷新章节目录");
    },
    async jumpToHeading(heading) {
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
    dispose() {
      if (disposed) {
        return;
      }
      disposed = true;
      clearReadyTimeout();
      window.removeEventListener("message", handleMessage);
      rejectPendingRequests(createBridgeError("文档桥接已销毁，请重新加载编辑器。"));
      readyRejecter?.(createBridgeError("文档桥接已销毁，请重新加载编辑器。"));
      readyPromise = null;
      readyResolver = null;
      readyRejecter = null;
      readyPayload = null;
    }
  };
}
