import { buildApiUrl, createAccessContextHeaders } from "../../lib/api";

const LLM_MESSAGE_STREAM_PATH = "/api/llm/messages/stream";

// 这里不用 EventSource，而是自己用 fetch + reader 解析 SSE：
// Phase 14.2 的流式接口是 POST，且必须携带 JSON body、鉴权头和可中止控制。
export function startLlmMessageStream(payload, options = {}) {
  const {
    onStarted,
    onDelta,
    onMeta,
    onCompleted,
    onCancelled,
    onError,
    onComplete
  } = options;

  const controller = new AbortController();
  const decoder = new TextDecoder();
  let reader = null;
  let resolveReady;
  let rejectReady;
  let readyResolved = false;

  const ready = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });

  const done = (async () => {
    let buffer = "";
    try {
      const response = await fetch(buildApiUrl(LLM_MESSAGE_STREAM_PATH), {
        method: "POST",
        headers: createAccessContextHeaders({
          "Content-Type": "application/json",
          Accept: "text/event-stream"
        }),
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        const error = new Error(payload?.message || `请求失败，HTTP ${response.status}`);
        error.status = response.status;
        error.errorCode = payload?.errorCode || "";
        error.payload = payload;
        throw error;
      }
      if (!response.body || typeof response.body.getReader !== "function") {
        throw new Error("llm stream response body is not readable");
      }

      reader = response.body.getReader();
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
            onStarted,
            onDelta,
            onMeta,
            onCompleted,
            onCancelled,
            onError
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
    ready,
    done,
    abort() {
      controller.abort();
      reader?.cancel?.().catch(() => {});
      return done;
    }
  };
}

function extractCompleteFrames(buffer) {
  // SSE 以空行分帧，reader 每次 read() 可能只拿到半帧，这里负责把完整帧和残留 buffer 拆开。
  const frames = [];
  let remaining = buffer;
  let separatorIndex = remaining.indexOf("\n\n");

  while (separatorIndex >= 0) {
    frames.push(remaining.slice(0, separatorIndex));
    remaining = remaining.slice(separatorIndex + 2);
    separatorIndex = remaining.indexOf("\n\n");
  }

  return {
    frames,
    buffer: remaining
  };
}

function dispatchFrame(frame, handlers) {
  if (!frame.trim()) {
    return;
  }
  // 浏览器只识别本项目定义的事件名，不感知服务端内部的 provider 细节。
  const event = parseSseFrame(frame);
  switch (event.name) {
    case "request-started":
      handlers.onStarted?.(event.payload);
      break;
    case "assistant-delta":
      handlers.onDelta?.(event.payload);
      break;
    case "assistant-meta":
      handlers.onMeta?.(event.payload);
      break;
    case "assistant-completed":
      handlers.onCompleted?.(event.payload);
      break;
    case "assistant-cancelled":
      handlers.onCancelled?.(event.payload);
      break;
    case "assistant-error":
      handlers.onError?.(event.payload);
      break;
    default:
      break;
  }
}

function parseSseFrame(frame) {
  // 只解析 SSE 标准字段 event/data；多行 data 会在这里重新拼回一个 JSON 字符串。
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

  return {
    name,
    payload: parseEventPayload(dataLines.join("\n"))
  };
}

function parseEventPayload(rawData) {
  if (!rawData) {
    return null;
  }
  // 事件体默认是 JSON DTO；解析失败时退回原始字符串，方便定位协议异常。
  try {
    return JSON.parse(rawData);
  } catch {
    return rawData;
  }
}
