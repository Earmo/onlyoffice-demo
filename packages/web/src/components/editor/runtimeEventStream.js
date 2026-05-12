import { buildApiUrl, createAccessContextHeaders } from "../../lib/api";

const RUNTIME_EVENT_PATH = documentId => `/api/document-runtime/${encodeURIComponent(documentId)}/runtime-events`;

// Phase 14.1 不使用原生 EventSource，而是自己走 fetch + reader。
// 这么做有三个明确原因：
// 1. 原生 EventSource 不能带当前项目依赖的 access context 自定义请求头；
// 2. 我们需要在文档切换、showConsole 关闭、组件卸载时精确 abort 当前流；
// 3. 我们希望把“服务器正常关闭流”和“流异常失败”区分开，交给外层状态机分别处理。
/**
 * 建立文档运行态 SSE 流。
 *
 * @param {object} options - 流式订阅配置。
 * @param {string} options.documentId - 当前文档 ID。
 * @param {(payload: object) => void} [options.onSaveStatus] - 保存状态事件回调。
 * @param {(payload: object) => void} [options.onSessionActive] - 编辑会话活跃事件回调。
 * @param {(payload: object) => void} [options.onKeepalive] - 服务端保活事件回调。
 * @param {(payload: object) => void} [options.onRuntimeError] - 服务端运行态错误事件回调。
 * @param {(error: Error) => void} [options.onError] - fetch/read 失败回调。
 * @param {() => void} [options.onComplete] - 服务端正常结束流时触发。
 * @returns {{abort: () => Promise<void>, ready: Promise<void>}} 流控制句柄。
 */
export function startRuntimeEventStream(options) {
  const {
    documentId,
    onSaveStatus,
    onSessionActive,
    onKeepalive,
    onRuntimeError,
    onError,
    onComplete
  } = options;

  const controller = new AbortController();
  const decoder = new TextDecoder();
  let reader = null;
  let readyResolved = false;
  let resolveReady;
  let rejectReady;

  const ready = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });

  const streamPromise = (async () => {
    // `buffer` 是这个解析器最关键的状态。
    // fetch streaming 下，一次 read() 只保证返回“当前拿到的一块字节”，不保证对齐到 SSE frame：
    // - 一个 JSON 可能被拆成两段；
    // - `event:` 和 `data:` 甚至可能分开到两次 read()；
    // - 多行 data 也可能跨 chunk。
    // 所以只能持续把内容追加到 buffer，等看到空行分隔符后再切完整 frame。
    let buffer = "";

    try {
      const response = await fetch(buildApiUrl(RUNTIME_EVENT_PATH(documentId)), {
        headers: createAccessContextHeaders({ Accept: "text/event-stream" }),
        signal: controller.signal
      });

      if (!response.ok) {
        throw new Error(`runtime-events request failed with HTTP ${response.status}`);
      }
      if (!response.body || typeof response.body.getReader !== "function") {
        throw new Error("runtime-events response body is not readable");
      }

      reader = response.body.getReader();
      // ready 只代表“链路已经建起来，可以开始读流”：
      // - HTTP 返回 200；
      // - response.body 可读；
      // - reader 已经拿到。
      // 它不等第一个 save-status 或 keepalive。
      // 这样 EditorShell 一看到 ready，就能停掉 save-status polling，
      // 不需要再等首帧到达才切换主通道。
      readyResolved = true;
      resolveReady();

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        buffer = buffer.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
        const parsed = extractCompleteFrames(buffer);
        buffer = parsed.buffer;

        for (const frame of parsed.frames) {
          dispatchFrame(frame, {
            onSaveStatus,
            onSessionActive,
            onKeepalive,
            onRuntimeError
          });
        }
      }

      if (!controller.signal.aborted) {
        onComplete?.();
      }
    } catch (error) {
      if (error?.name === "AbortError" || controller.signal.aborted) {
        if (!readyResolved) {
          rejectReady(error);
        }
        return;
      }

      onError?.(error);
      if (!readyResolved) {
        rejectReady(error);
      }
    } finally {
      decoder.decode();
      reader?.releaseLock?.();
    }
  })();

  return {
    abort() {
      controller.abort();
      reader?.cancel?.().catch(() => {});
      return streamPromise;
    },
    ready
  };
}

/**
 * 从累计 buffer 中切出所有完整 SSE frame。
 *
 * @param {string} buffer - 当前已经解码但尚未全部消费的文本。
 * @returns {{frames: string[], buffer: string}} 完整帧数组和未完成的尾部文本。
 */
function extractCompleteFrames(buffer) {
  // SSE 规范里，空行表示“一个 frame 结束”。
  // 这里每次只切出已经完整结束的 frame，最后那个没遇到空行的尾巴继续留给下次 read()。
  const frames = [];
  let remaining = buffer;
  let separatorIndex = remaining.indexOf("\n\n");

  while (separatorIndex >= 0) {
    const frame = remaining.slice(0, separatorIndex);
    frames.push(frame);
    remaining = remaining.slice(separatorIndex + 2);
    separatorIndex = remaining.indexOf("\n\n");
  }

  return {
    frames,
    buffer: remaining
  };
}

/**
 * 根据事件名把单个 SSE frame 分发给对应处理器。
 *
 * @param {string} frame - 已完整结束的 SSE frame。
 * @param {object} handlers - 运行态事件处理器集合。
 */
function dispatchFrame(frame, handlers) {
  // 14.1 只关心四类命名事件：
  // - save-status：真正驱动右侧保存状态卡片；
  // - session-active：表示当前 actor 已经进入活跃编辑态；
  // - keepalive：主要用于链路保活和调试；
  // - runtime-error：流还没完全断时，服务端主动给出的失败原因。
  //
  // comment frame、空 frame、未知事件都直接忽略，让外层状态机保持最小表面积。
  if (!frame.trim()) {
    return;
  }

  const event = parseSseFrame(frame);
  switch (event.name) {
    case "save-status":
      handlers.onSaveStatus?.(event.payload);
      break;
    case "session-active":
      handlers.onSessionActive?.(event.payload);
      break;
    case "keepalive":
      handlers.onKeepalive?.(event.payload);
      break;
    case "runtime-error":
      handlers.onRuntimeError?.(event.payload);
      break;
    default:
      break;
  }
}

/**
 * 解析本项目使用的 SSE 最小字段集。
 *
 * @param {string} frame - 单个 SSE frame 文本。
 * @returns {{name: string, payload: unknown}} 事件名和 data 解析结果。
 */
function parseSseFrame(frame) {
  // 这里只实现 14.1 真正会用到的最小 SSE 子集：
  // - `event:` 决定事件名；
  // - `data:` 支持多行，最后按 `\n` 拼回；
  // - 以 `:` 开头的 comment line 跳过。
  // `id:` / `retry:` 当前前端状态机没有消费，所以不在这里额外建复杂度。
  const lines = frame.split("\n");
  let name = "message";
  const dataLines = [];

  for (const line of lines) {
    if (!line || line.startsWith(":")) {
      continue;
    }

    const separatorIndex = line.indexOf(":");
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
    let value = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : "";
    if (value.startsWith(" ")) {
      value = value.slice(1);
    }

    if (field === "event") {
      name = value;
      continue;
    }
    if (field === "data") {
      dataLines.push(value);
    }
  }

  const rawData = dataLines.join("\n");
  return {
    name,
    payload: parseEventPayload(rawData)
  };
}

/**
 * 将 SSE data 字段解析成业务 payload。
 *
 * @param {string} rawData - data 行拼接后的原始文本。
 * @returns {unknown} JSON 对象、字符串或 null。
 */
function parseEventPayload(rawData) {
  if (!rawData) {
    return null;
  }

  try {
    return JSON.parse(rawData);
  } catch {
    return rawData;
  }
}
