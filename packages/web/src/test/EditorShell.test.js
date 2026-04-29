import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";
import * as runtimeEventStreamModule from "../components/editor/runtimeEventStream.js";

const bridgeMocks = vi.hoisted(() => ({
  createOnlyofficeBridge: vi.fn(),
  waitForReady: vi.fn(),
  captureSelectedText: vi.fn(),
  refreshOutline: vi.fn(),
  jumpToHeading: vi.fn(),
  insertHtml: vi.fn(),
  dispose: vi.fn()
}));

const writeBackStoreMock = vi.hoisted(() => ({
  state: {
    status: "idle",
    errorMsg: ""
  },
  useWriteBackStore: vi.fn(() => writeBackStoreMock.state),
  reset() {
    writeBackStoreMock.state.status = "idle";
    writeBackStoreMock.state.errorMsg = "";
  }
}));

vi.mock("@onlyoffice/document-editor-vue", () => ({
  DocumentEditor: {
    name: "OnlyofficeDocumentEditorStub",
    props: ["documentServerUrl", "config", "events_onDocumentReady"],
    template: "<div class='onlyoffice-stub'>{{ config?.document?.title }}</div>",
    mounted() {
      this.events_onDocumentReady?.();
    }
  }
}));

vi.mock("../components/editor/onlyofficeBridge.js", () => ({
  createOnlyofficeBridge: bridgeMocks.createOnlyofficeBridge
}));

vi.mock("../stores/writeBackStore.js", () => ({
  useWriteBackStore: writeBackStoreMock.useWriteBackStore
}));

vi.mock("../components/editor/EditorAiWorkbench.vue", () => ({
  default: {
    name: "EditorAiWorkbenchStub",
    props: ["documentTitle", "runtimeContext", "loading", "closing"],
    emits: ["capture-selection", "refresh-outline", "jump-to-heading", "insert-image", "insert-html"],
    template: `
      <div class="ai-workbench-stub">
        <p class="workbench-title">{{ documentTitle }}</p>
        <p class="runtime-document">{{ runtimeContext.documentId }}</p>
        <p class="runtime-selection">{{ runtimeContext.selectedText }}</p>
        <p class="runtime-save-status">{{ runtimeContext.saveStatus?.state || 'none' }}</p>
        <button class="emit-capture" @click="$emit('capture-selection')">capture</button>
        <button class="emit-refresh" @click="$emit('refresh-outline')">refresh</button>
        <button class="emit-jump" @click="$emit('jump-to-heading', { id: 'heading-1', text: '一、项目背景', paragraphIndex: 3 })">jump</button>
        <button class="emit-insert-html" @click="$emit('insert-html', { html: '<p>hello</p>' })">insert html</button>
      </div>
    `
  }
}));

import EditorShell from "../components/editor/EditorShell.vue";

describe("runtimeEventStream", () => {
  it("应使用 access context headers 建立 runtime-events 流，并处理跨 chunks 拆开的 save-status frame", async () => {
    // Regression: save-status frame split across chunks must keep a partial frame buffer.
    const onSaveStatus = vi.fn();
    const onSessionActive = vi.fn();
    const onKeepalive = vi.fn();
    const onRuntimeError = vi.fn();
    const onError = vi.fn();
    const onComplete = vi.fn();

    fetch.mockResolvedValueOnce(createRuntimeEventResponse([
      "event: save-st",
      "atus\ndata: {\"documentId\":\"doc 1/test\",\"state\":\"saved\",",
      "\"message\":\"ok\"}\n\n",
      "event: session-active\ndata: {\"documentId\":\"doc 1/test\",\"active\":true}\n\n",
      ": keepalive comment\n",
      "event: keepalive\ndata: {\"documentId\":\"doc 1/test\",\n",
      "data: \"tick\":1}\n\n",
      "event: runtime-error\ndata: {\"documentId\":\"doc 1/test\",\"code\":\"RUNTIME_DOWN\"}\n\n"
    ]));

    const stream = runtimeEventStreamModule.startRuntimeEventStream({
      documentId: "doc 1/test",
      onSaveStatus,
      onSessionActive,
      onKeepalive,
      onRuntimeError,
      onError,
      onComplete
    });

    await stream.ready;
    await flushPromises();

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/documents/doc%201%2Ftest/runtime-events"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Accept: "text/event-stream",
          "X-External-User-Id": "starter-user"
        }),
        signal: expect.any(AbortSignal)
      })
    );
    expect(onSaveStatus).toHaveBeenCalledWith(expect.objectContaining({
      documentId: "doc 1/test",
      state: "saved"
    }));
    expect(onSessionActive).toHaveBeenCalledWith(expect.objectContaining({ active: true }));
    expect(onKeepalive).toHaveBeenCalledWith(expect.objectContaining({
      documentId: "doc 1/test",
      tick: 1
    }));
    expect(onRuntimeError).toHaveBeenCalledWith(expect.objectContaining({ code: "RUNTIME_DOWN" }));
    expect(onError).not.toHaveBeenCalled();
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it("应在 ready 后 clean completion 调用 onComplete，但不调用 onError", async () => {
    // Regression: onComplete fires only for clean completion after ready resolves.
    const onComplete = vi.fn();
    const onError = vi.fn();

    fetch.mockResolvedValueOnce(createRuntimeEventResponse([
      "event: keepalive\ndata: {\"documentId\":\"doc-1\"}\n\n"
    ]));

    const stream = runtimeEventStreamModule.startRuntimeEventStream({
      documentId: "doc-1",
      onSaveStatus: vi.fn(),
      onSessionActive: vi.fn(),
      onKeepalive: vi.fn(),
      onRuntimeError: vi.fn(),
      onError,
      onComplete
    });

    await stream.ready;
    await flushPromises();

    expect(onComplete).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
  });

  it("应在 abort 时停止 reader，且不触发 onComplete 或 onError", async () => {
    const onComplete = vi.fn();
    const onError = vi.fn();
    const response = createDeferredRuntimeEventResponse();

    fetch.mockResolvedValueOnce(response);

    const stream = runtimeEventStreamModule.startRuntimeEventStream({
      documentId: "doc-1",
      onSaveStatus: vi.fn(),
      onSessionActive: vi.fn(),
      onKeepalive: vi.fn(),
      onRuntimeError: vi.fn(),
      onError,
      onComplete
    });

    await stream.ready;
    stream.abort();
    response.rejectPendingRead(abortError());
    await flushPromises();

    expect(onComplete).not.toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
  });
});

describe("EditorShell", () => {
  beforeEach(() => {
    fetch.mockReset();
    vi.useRealTimers();
    bridgeMocks.waitForReady.mockReset();
    bridgeMocks.captureSelectedText.mockReset();
    bridgeMocks.refreshOutline.mockReset();
    bridgeMocks.jumpToHeading.mockReset();
    bridgeMocks.insertHtml.mockReset();
    bridgeMocks.dispose.mockReset();
    bridgeMocks.createOnlyofficeBridge.mockReset();
    writeBackStoreMock.reset();

    bridgeMocks.waitForReady.mockResolvedValue({ capability: "plugin" });
    bridgeMocks.captureSelectedText.mockResolvedValue({ text: "第一段选中文本", emptySelection: false });
    bridgeMocks.refreshOutline.mockResolvedValue({
      headings: [
        { id: "heading-1", text: "一、项目背景", level: 1, styleName: "Heading 1", paragraphIndex: 3 }
      ],
      emptyOutline: false
    });
    bridgeMocks.jumpToHeading.mockResolvedValue({ id: "heading-1", paragraphIndex: 3 });
    bridgeMocks.createOnlyofficeBridge.mockImplementation(() => ({
      capability: "plugin",
      waitForReady: bridgeMocks.waitForReady,
      captureSelectedText: bridgeMocks.captureSelectedText,
      refreshOutline: bridgeMocks.refreshOutline,
      jumpToHeading: bridgeMocks.jumpToHeading,
      insertHtml: bridgeMocks.insertHtml,
      dispose: bridgeMocks.dispose
    }));

    vi.spyOn(runtimeEventStreamModule, "startRuntimeEventStream").mockImplementation(() => ({
      abort: vi.fn(),
      ready: Promise.resolve()
    }));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("应在编辑模式挂载 EditorAiWorkbench，并在显式关闭后卸载时不重复请求 close-session", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(editorConfigPayload("路线图.docx")))
      .mockResolvedValueOnce(jsonResponse(saveStatusPayload()))
      .mockResolvedValueOnce(jsonResponse(saveStatusPayload()))
      .mockResolvedValueOnce(jsonResponse(closedStatusPayload()));

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });
    await flushPromises();

    expect(String(fetch.mock.calls[0][0])).toContain("/api/documents/get/editor-config");
    expect(wrapper.find(".drawer-collapse-btn").exists()).toBe(true);

    await wrapper.find(".drawer-collapse-btn").trigger("click");
    await flushPromises();

    expect(wrapper.find(".ai-workbench-stub").exists()).toBe(true);
    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.text()).toContain("doc-1");

    await wrapper.vm.closeEditingSession();
    await flushPromises();
    wrapper.unmount();
    await flushPromises();

    const closeCalls = fetch.mock.calls.filter(call => String(call[0]).includes("/api/documents/close/session"));
    expect(closeCalls).toHaveLength(1);
  });

  it("应通过 EditorAiWorkbench 事件继续驱动抓取选区、刷新目录和标题跳转", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(editorConfigPayload("路线图.docx")))
      .mockResolvedValueOnce(jsonResponse(saveStatusPayload()));

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });
    await flushPromises();

    await wrapper.find(".drawer-collapse-btn").trigger("click");
    await flushPromises();

    await wrapper.find(".emit-capture").trigger("click");
    await flushPromises();
    expect(bridgeMocks.captureSelectedText).toHaveBeenCalledTimes(1);

    await wrapper.find(".emit-refresh").trigger("click");
    await flushPromises();
    expect(bridgeMocks.refreshOutline).toHaveBeenCalled();

    await wrapper.find(".emit-jump").trigger("click");
    await flushPromises();
    expect(bridgeMocks.jumpToHeading).toHaveBeenCalledWith(
      expect.objectContaining({ id: "heading-1", paragraphIndex: 3 })
    );
  });

  describe("handleInsertHtml", () => {
    it("bridge 未就绪时应将 store.status 设为 error", async () => {
      bridgeMocks.waitForReady.mockRejectedValueOnce(new Error("bridge pending"));
      fetch.mockResolvedValueOnce(jsonResponse(editorConfigPayload("路线图.docx")));

      const wrapper = mount(EditorShell, {
        props: {
          documentId: "doc-1",
          documentTitle: "路线图.docx"
        }
      });
      await flushPromises();

      await wrapper.vm.handleInsertHtml({ html: "<p>test</p>" });

      const store = writeBackStoreMock.state;
      expect(store.status).toBe("error");
      expect(store.errorMsg).toMatch(/未就绪/);
      expect(bridgeMocks.insertHtml).not.toHaveBeenCalled();
    });

    it("bridge.insertHtml 成功时应将 store.status 设为 success", async () => {
      bridgeMocks.insertHtml.mockResolvedValue({ success: true });
      fetch.mockResolvedValueOnce(jsonResponse(editorConfigPayload("路线图.docx")));

      const wrapper = mount(EditorShell, {
        props: {
          documentId: "doc-1",
          documentTitle: "路线图.docx"
        }
      });
      await flushPromises();

      await wrapper.vm.handleInsertHtml({ html: "<p>hello</p>" });

      expect(bridgeMocks.insertHtml).toHaveBeenCalledWith("<p>hello</p>");
      expect(writeBackStoreMock.state.status).toBe("success");
    });

    it("bridge.insertHtml 抛出异常时应将 store.status 设为 error 并透传 errorMsg", async () => {
      bridgeMocks.insertHtml.mockRejectedValue(new Error("PasteHtml timeout"));
      fetch.mockResolvedValueOnce(jsonResponse(editorConfigPayload("路线图.docx")));

      const wrapper = mount(EditorShell, {
        props: {
          documentId: "doc-1",
          documentTitle: "路线图.docx"
        }
      });
      await flushPromises();

      await wrapper.vm.handleInsertHtml({ html: "<p>fail</p>" });

      const store = writeBackStoreMock.state;
      expect(store.status).toBe("error");
      expect(store.errorMsg).toBe("PasteHtml timeout");
    });
  });

  it("应在 close-session 进行中复用同一个请求而不是重复发送", async () => {
    let resolveSaveRequest;
    let saveCallCount = 0;

    fetch.mockImplementation((url) => {
      const urlStr = String(url);
      if (urlStr.includes("/editor-config")) {
        return Promise.resolve(jsonResponse(editorConfigPayload("路线图.docx")));
      }
      if (urlStr.includes("/get/save-status")) {
        return Promise.resolve(jsonResponse(saveStatusPayload()));
      }
      if (urlStr.includes("/save")) {
        saveCallCount++;
        return new Promise(resolve => {
          resolveSaveRequest = () => resolve(jsonResponse(saveStatusPayload({
            lastSavedTime: "2026-03-25T10:00:02Z"
          })));
        });
      }
      if (urlStr.includes("/close/session")) {
        return Promise.resolve(jsonResponse(closedStatusPayload({
          lastSavedTime: "2026-03-25T10:00:02Z"
        })));
      }
      return Promise.reject(new Error("unexpected fetch: " + urlStr));
    });

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });
    await flushPromises();

    const firstClose = wrapper.vm.closeEditingSession();
    const secondClose = wrapper.vm.closeEditingSession();
    await new Promise(resolve => setTimeout(resolve, 100));
    await flushPromises();

    expect(saveCallCount).toBe(1);

    resolveSaveRequest();
    const [firstPayload, secondPayload] = await Promise.all([firstClose, secondClose]);
    expect(firstPayload.state).toBe("saved");
    expect(secondPayload.state).toBe("saved");
  });

  it("应在预览模式不展示 EditorAiWorkbench", async () => {
    fetch.mockResolvedValueOnce(jsonResponse(editorConfigPayload("预览稿.docx", "view")));

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-2",
        documentTitle: "预览稿.docx",
        readonly: true,
        showConsole: false
      }
    });
    await flushPromises();

    expect(String(fetch.mock.calls[0][0])).toContain("/api/documents/get/editor-config");
    expect(wrapper.find(".floating-console").isVisible()).toBe(false);
    expect(wrapper.find(".drawer-collapse-btn").exists()).toBe(false);
  });

  it("应在 SSE healthy 时停止 save-status polling 与 heartbeat 轮询并消费 runtime-events", async () => {
    vi.useFakeTimers();
    const stream = createRuntimeStreamController();
    const startStreamSpy = queueRuntimeStreamControllers([stream]);
    installEditorFetchMock();

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });

    await flushTimersAndPromises();
    stream.resolveReady();
    stream.emitSaveStatus(saveStatusPayload({ documentId: "doc-1", message: "来自 runtime-events" }));
    await flushTimersAndPromises();

    await vi.advanceTimersByTimeAsync(15000);
    await flushTimersAndPromises();

    expect(startStreamSpy).toHaveBeenCalledWith(expect.objectContaining({ documentId: "doc-1" }));
    expect(wrapper.vm.saveStatus?.message).toBe("来自 runtime-events");
    expect(countFetchCalls("/api/documents/doc-1/save-status")).toBe(0);
    expect(countFetchCalls("/api/documents/doc-1/editing-sessions/heartbeat")).toBe(0);
  });

  it("应在 stream 失败后恢复 save-status fallback，并按 1000/2000/4000/8000/15000ms 退避重试", async () => {
    vi.useFakeTimers();
    const clearTimeoutSpy = vi.spyOn(window, "clearTimeout");
    const controllers = Array.from({ length: 6 }, () => createRuntimeStreamController());
    const startStreamSpy = queueRuntimeStreamControllers(controllers);
    installEditorFetchMock();

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });

    await flushTimersAndPromises();
    controllers[0].resolveReady();
    await flushTimersAndPromises();
    controllers[0].emitError(new Error("stream down"));
    await flushTimersAndPromises();
    controllers[0].emitRuntimeError({ documentId: "doc-1", code: "duplicate-failure" });
    await flushTimersAndPromises();
    expect(clearTimeoutSpy).toHaveBeenCalled();

    const retriesObserved = [1000, 2000, 4000, 8000, 15000];
    let expectedCalls = 1;
    for (let failureIndex = 0; failureIndex < retriesObserved.length; failureIndex += 1) {
      const retryDelay = retriesObserved[failureIndex];
      await vi.advanceTimersByTimeAsync(retryDelay);
      await flushTimersAndPromises();
      expectedCalls += 1;
      expect(startStreamSpy).toHaveBeenCalledTimes(expectedCalls);
      expect(countFetchCalls("/api/documents/get/save-status")).toBeGreaterThan(0);
      expect(countFetchCalls("/api/documents/doc-1/editing-sessions/heartbeat")).toBe(0);
      if (controllers[failureIndex + 1]) {
        controllers[failureIndex + 1].emitError(new Error(`retry-${failureIndex}`));
        await flushTimersAndPromises();
      }
    }

    wrapper.unmount();
  });

  it("应在 clean completion 后立即重连而不重新激活 REST fallback polling", async () => {
    vi.useFakeTimers();
    const firstStream = createRuntimeStreamController();
    const secondStream = createRuntimeStreamController();
    const startStreamSpy = queueRuntimeStreamControllers([firstStream, secondStream]);
    installEditorFetchMock();

    mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });

    await flushTimersAndPromises();
    firstStream.resolveReady();
    await flushTimersAndPromises();

    firstStream.complete();
    secondStream.resolveReady();
    await flushTimersAndPromises();
    await vi.advanceTimersByTimeAsync(5000);
    await flushTimersAndPromises();

    expect(startStreamSpy).toHaveBeenCalledTimes(2);
    expect(countFetchCalls("/api/documents/doc-1/save-status")).toBe(0);
    expect(countFetchCalls("/api/documents/doc-1/editing-sessions/heartbeat")).toBe(0);
  });

  it("应在 showConsole=false 时继续使用 SSE 维持编辑态，readonly 则不启动运行态通道", async () => {
    vi.useFakeTimers();
    const hiddenConsoleStream = createRuntimeStreamController();
    const startStreamSpy = queueRuntimeStreamControllers([hiddenConsoleStream]);
    installEditorFetchMock();

    const hiddenConsoleWrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx",
        showConsole: false
      }
    });

    await flushTimersAndPromises();
    hiddenConsoleStream.resolveReady();
    await flushTimersAndPromises();
    await vi.advanceTimersByTimeAsync(5000);
    await flushTimersAndPromises();

    expect(startStreamSpy).toHaveBeenCalledWith(expect.objectContaining({ documentId: "doc-1" }));
    expect(countFetchCalls("/api/documents/doc-1/save-status")).toBe(0);
    expect(countFetchCalls("/api/documents/doc-1/editing-sessions/heartbeat")).toBe(0);

    hiddenConsoleWrapper.unmount();
    fetch.mockClear();

    const readonlyWrapper = mount(EditorShell, {
      props: {
        documentId: "doc-2",
        documentTitle: "预览稿.docx",
        readonly: true
      }
    });

    await flushTimersAndPromises();
    await vi.advanceTimersByTimeAsync(10000);
    await flushTimersAndPromises();

    expect(startStreamSpy).toHaveBeenCalledTimes(1);
    expect(countFetchCalls("/api/documents/doc-2/save-status")).toBe(0);
    expect(countFetchCalls("/api/documents/doc-2/editing-sessions/heartbeat")).toBe(0);

    readonlyWrapper.unmount();
  });

  it("应在文档切换时 abort 旧 stream，并忽略旧 documentId 的 save-status", async () => {
    vi.useFakeTimers();
    const firstStream = createRuntimeStreamController();
    const secondStream = createRuntimeStreamController();
    queueRuntimeStreamControllers([firstStream, secondStream]);
    installEditorFetchMock();

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });

    await flushTimersAndPromises();
    firstStream.resolveReady();
    firstStream.emitSaveStatus(saveStatusPayload({ documentId: "doc-1", message: "旧文档状态" }));
    await flushTimersAndPromises();

    await wrapper.setProps({
      documentId: "doc-2",
      documentTitle: "第二份文档.docx"
    });
    await flushTimersAndPromises();

    secondStream.resolveReady();
    firstStream.emitSaveStatus(saveStatusPayload({ documentId: "doc-1", message: "不应污染当前页面" }));
    secondStream.emitSaveStatus(saveStatusPayload({ documentId: "doc-2", message: "新文档状态" }));
    await flushTimersAndPromises();

    expect(firstStream.abort).toHaveBeenCalled();
    expect(wrapper.vm.saveStatus?.documentId).toBe("doc-2");
    expect(wrapper.vm.saveStatus?.message).toBe("新文档状态");
  });
});

function editorConfigPayload(title, mode = "edit") {
  return {
    documentServerUrl: "https://docs.example.test/",
    config: {
      document: {
        title
      },
      editorConfig: {
        mode
      }
    }
  };
}

function saveStatusPayload(overrides = {}) {
  return {
    state: "saved",
    message: "最新修改已成功回写到共享存储。",
    lastCallbackStatus: 2,
    lastCallbackTime: "2026-03-25T10:00:00Z",
    lastSavedTime: "2026-03-25T10:00:01Z",
    recentEvents: [],
    ...overrides
  };
}

function closedStatusPayload(overrides = {}) {
  return {
    documentId: "doc-1",
    state: "saved",
    message: "当前用户已离开编辑器，文档已退出活跃编辑状态。",
    lastCallbackStatus: 2,
    lastCallbackTime: "2026-03-25T10:00:00Z",
    lastSavedTime: "2026-03-25T10:00:01Z",
    recentEvents: [],
    ...overrides
  };
}

function queueRuntimeStreamControllers(controllers) {
  let index = 0;
  return vi.spyOn(runtimeEventStreamModule, "startRuntimeEventStream").mockImplementation((options) => {
    const controller = controllers[index];
    index += 1;
    if (!controller) {
      throw new Error("unexpected runtime stream start");
    }
    controller.options = options;
    return {
      abort: controller.abort,
      ready: controller.ready
    };
  });
}

function createRuntimeStreamController() {
  let resolveReady;
  let rejectReady;
  const ready = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });
  ready.catch(() => {});

  return {
    options: null,
    abort: vi.fn(),
    ready,
    resolveReady() {
      resolveReady();
    },
    rejectReady(error) {
      rejectReady(error);
    },
    emitSaveStatus(payload) {
      this.options?.onSaveStatus?.(payload);
    },
    emitRuntimeError(payload) {
      this.options?.onRuntimeError?.(payload);
    },
    emitError(error) {
      this.options?.onError?.(error);
    },
    complete() {
      this.options?.onComplete?.();
    }
  };
}

function installEditorFetchMock() {
  fetch.mockImplementation((url) => {
    const urlString = String(url);
    const documentId = extractDocumentId(urlString);

    if (urlString.includes("/editor-config")) {
      const readonly = urlString.includes("readonly=true");
      return jsonResponse(editorConfigPayload(readonly ? "预览稿.docx" : "路线图.docx", readonly ? "view" : "edit"));
    }
    if (urlString.includes("/get/save-status")) {
      return jsonResponse(saveStatusPayload({ documentId }));
    }
    if (urlString.endsWith("/save")) {
      return jsonResponse(saveStatusPayload({ documentId }));
    }
    if (urlString.includes("/close/session")) {
      return jsonResponse(closedStatusPayload({ documentId }));
    }

    return Promise.reject(new Error(`unexpected fetch: ${urlString}`));
  });
}

function countFetchCalls(fragment) {
  return fetch.mock.calls.filter(([url]) => String(url).includes(fragment)).length;
}

function extractDocumentId(url) {
  const match = String(url).match(/\/api\/documents\/([^/]+)/);
  return match ? decodeURIComponent(match[1]) : "doc-1";
}

async function flushTimersAndPromises() {
  await Promise.resolve();
  await Promise.resolve();
  await vi.advanceTimersByTimeAsync(0);
}

function createRuntimeEventResponse(chunks) {
  const encoder = new TextEncoder();
  let index = 0;

  return {
    ok: true,
    status: 200,
    body: {
      getReader() {
        return {
          async read() {
            if (index >= chunks.length) {
              return { done: true, value: undefined };
            }
            const value = encoder.encode(chunks[index]);
            index += 1;
            return { done: false, value };
          },
          releaseLock() {}
        };
      }
    }
  };
}

function createDeferredRuntimeEventResponse() {
  const encoder = new TextEncoder();
  let settled = false;
  let rejectPendingRead = () => {};

  return {
    ok: true,
    status: 200,
    body: {
      getReader() {
        return {
          async read() {
            if (settled) {
              return { done: true, value: undefined };
            }
            settled = true;
            return new Promise((resolve, reject) => {
              rejectPendingRead = reject;
              setTimeout(() => resolve({ done: false, value: encoder.encode("event: keepalive\n") }), 50);
            });
          },
          releaseLock() {}
        };
      }
    },
    rejectPendingRead(error) {
      rejectPendingRead(error);
    }
  };
}

function abortError() {
  const error = new Error("aborted");
  error.name = "AbortError";
  return error;
}
