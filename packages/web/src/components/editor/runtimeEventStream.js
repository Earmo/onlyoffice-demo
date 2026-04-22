import { buildApiUrl, createAccessContextHeaders } from "../../lib/api";

const RUNTIME_EVENT_PATH = documentId => `/api/documents/${encodeURIComponent(documentId)}/runtime-events`;

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

function extractCompleteFrames(buffer) {
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

function dispatchFrame(frame, handlers) {
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

function parseSseFrame(frame) {
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
