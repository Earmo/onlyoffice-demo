import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "./helpers";

const elementPlusMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  prompt: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}));

const apiMocks = vi.hoisted(() => ({
  getLlmCapability: vi.fn(),
  listLlmSessions: vi.fn(),
  createLlmSession: vi.fn(),
  getLlmSession: vi.fn(),
  startLlmMessageStream: vi.fn(),
  getLlmRequest: vi.fn(),
  cancelLlmRequest: vi.fn()
}));

vi.mock("../components/editor/editorAiApi.js", () => apiMocks);
vi.mock("element-plus", async importOriginal => {
  const actual = await importOriginal();
  return {
    ...actual,
    ElMessage: {
      success: elementPlusMocks.success,
      error: elementPlusMocks.error,
      info: elementPlusMocks.info
    },
    ElMessageBox: {
      confirm: elementPlusMocks.confirm,
      prompt: elementPlusMocks.prompt
    }
  };
});

import EditorAiWorkbench from "../components/editor/EditorAiWorkbench.vue";

describe("EditorAiWorkbench", () => {
  beforeEach(() => {
    Object.values(apiMocks).forEach(mock => mock.mockReset());
    Object.values(elementPlusMocks).forEach(mock => mock.mockReset());
    elementPlusMocks.confirm.mockResolvedValue("confirm");
    apiMocks.getLlmCapability.mockResolvedValue({
      documentId: "doc-1",
      llmAvailable: true,
      disabledReason: null,
      provider: "stub-provider",
      model: "fake-gpt",
      supportsUpstreamCancel: false,
      streamMode: true,
      defaultProvider: "stub-provider",
      defaultModel: "fake-gpt",
      availableProviders: [
        {
          provider: "stub-provider",
          label: "Stub Provider",
          defaultModel: "fake-gpt",
          availableModels: ["fake-gpt", "fake-gpt-2"],
          supportsUpstreamCancel: false,
          streamEnabled: true
        }
      ]
    });
    apiMocks.listLlmSessions.mockResolvedValue([]);
    apiMocks.createLlmSession.mockResolvedValue({
      sessionId: "session-1",
      documentId: "doc-1",
      title: "引导会话",
      lastSnapshotText: "",
      lastSnapshotIsEmpty: true,
      lastHeadingId: "",
      lastHeadingText: "",
      messages: []
    });
    apiMocks.getLlmSession.mockResolvedValue({
      sessionId: "session-1",
      documentId: "doc-1",
      title: "引导会话",
      lastSnapshotText: "",
      lastSnapshotIsEmpty: true,
      lastHeadingId: "",
      lastHeadingText: "",
      messages: []
    });
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-1",
          sessionId: "session-1",
          assistantMessageId: "assistant-1",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onDelta?.({ requestId: "request-1", delta: "流式" });
        handlers.onDelta?.({ requestId: "request-1", delta: "回复" });
        handlers.onMeta?.({
          requestId: "request-1",
          usage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
          finishReason: "stop",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onCompleted?.({
          requestId: "request-1",
          sessionId: "session-1",
          assistantMessageId: "assistant-1",
          assistantText: "流式回复",
          usage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
          finishReason: "stop",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });
  });

  it("应展示 capability-disabled 和 disabledReason", async () => {
    apiMocks.getLlmCapability.mockResolvedValueOnce({
      documentId: "doc-1",
      llmAvailable: false,
      disabledReason: "LLM_DISABLED",
      provider: "stub-provider",
      model: "fake-gpt",
      supportsUpstreamCancel: false,
      streamMode: true,
      defaultProvider: "stub-provider",
      defaultModel: "fake-gpt",
      availableProviders: []
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.find(".capability-disabled").exists()).toBe(true);
    expect(wrapper.text()).toContain("LLM_DISABLED");
    expect(wrapper.text()).toContain("llmAvailable=false");
  });

  it("应忽略自动建会话 stale response", async () => {
    const firstSession = deferred();
    const secondSession = deferred();
    apiMocks.createLlmSession
      .mockImplementationOnce(() => firstSession.promise)
      .mockImplementationOnce(() => secondSession.promise);

    const wrapper = mountWorkbench({
      runtimeContext: runtimeContext({ documentId: "doc-1" })
    });
    await flushPromises();

    await wrapper.setProps({
      runtimeContext: runtimeContext({ documentId: "doc-2" })
    });
    await flushPromises();

    firstSession.resolve({
      sessionId: "session-stale",
      documentId: "doc-1",
      title: "旧会话",
      lastSnapshotText: "",
      lastSnapshotIsEmpty: true,
      lastHeadingId: "",
      lastHeadingText: "",
      messages: []
    });
    secondSession.resolve({
      sessionId: "session-fresh",
      documentId: "doc-2",
      title: "新会话",
      lastSnapshotText: "",
      lastSnapshotIsEmpty: true,
      lastHeadingId: "",
      lastHeadingText: "",
      messages: []
    });
    await flushPromises();

    expect(wrapper.text()).toContain("当前会话：新会话");
    expect(wrapper.text()).not.toContain("当前会话：旧会话");
  });

  it("应在流式事件完成后展示增量回复和 usage", async () => {
    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("帮我总结这一段");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    expect(apiMocks.startLlmMessageStream).toHaveBeenCalledWith(
      expect.objectContaining({
        provider: "stub-provider",
        model: "fake-gpt",
        retryConfirmed: false
      }),
      expect.any(Object)
    );
    expect(wrapper.text()).toContain("流式回复");
    expect(wrapper.text()).toContain("promptTokens: 10");
    expect(wrapper.text()).toContain("provider: stub-provider");
  });

  it("应把当前 provider/model 选择带入 payload", async () => {
    const wrapper = mountWorkbench();
    await flushPromises();

    wrapper.vm.selectedModel = "fake-gpt-2";
    await flushPromises();

    await wrapper.find("textarea").setValue("切模型发送");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    expect(apiMocks.startLlmMessageStream).toHaveBeenCalledWith(
      expect.objectContaining({
        provider: "stub-provider",
        model: "fake-gpt-2"
      }),
      expect.any(Object)
    );
  });

  it("应在取消后显示请求已取消", async () => {
    const abort = vi.fn(() => Promise.resolve());
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-2",
          sessionId: "session-1",
          assistantMessageId: "assistant-2",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort
      };
    });
    apiMocks.cancelLlmRequest.mockResolvedValueOnce({
      documentId: "doc-1",
      requestId: "request-2",
      sessionId: "session-1",
      assistantMessageId: "assistant-2",
      status: "cancelled",
      assistantText: "",
      usage: null,
      finishReason: "",
      providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" },
      errorCode: "LLM_REQUEST_CANCELLED",
      startedTime: "",
      finishedTime: ""
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("需要取消");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    await wrapper.find('button[title="取消发送"]').trigger("click");
    await flushPromises();

    expect(abort).toHaveBeenCalled();
    expect(apiMocks.cancelLlmRequest).toHaveBeenCalledWith("request-2", "doc-1");
    expect(wrapper.text()).toContain("请求已取消");
    expect(wrapper.text()).toContain("LLM_REQUEST_CANCELLED");
  });

  it("应在流异常断开后只回查一次最终态", async () => {
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-3",
          sessionId: "session-1",
          assistantMessageId: "assistant-3",
          provider: "stub-provider",
          model: "fake-gpt"
        });
        handlers.onDelta?.({ requestId: "request-3", delta: "半截" });
        handlers.onComplete?.();
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });
    apiMocks.getLlmRequest.mockResolvedValueOnce({
      documentId: "doc-1",
      requestId: "request-3",
      sessionId: "session-1",
      assistantMessageId: "assistant-3",
      status: "completed",
      assistantText: "最终结果",
      usage: { promptTokens: 1, completionTokens: 2, totalTokens: 3 },
      finishReason: "stop",
      providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" },
      errorCode: "",
      startedTime: "",
      finishedTime: ""
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("断流回查");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    expect(apiMocks.getLlmRequest).toHaveBeenCalledTimes(1);
    expect(apiMocks.getLlmRequest).toHaveBeenCalledWith("request-3", "doc-1");
    expect(wrapper.text()).toContain("最终结果");
  });

  it("应在切换会话时提示当前流式回复会被中断", async () => {
    const abort = vi.fn(() => Promise.resolve());
    apiMocks.listLlmSessions.mockResolvedValue([
      {
        sessionId: "session-1",
        documentId: "doc-1",
        title: "当前会话",
        updatedTime: "2026-04-23T10:00:00Z"
      },
      {
        sessionId: "session-2",
        documentId: "doc-1",
        title: "目标会话",
        updatedTime: "2026-04-23T10:01:00Z"
      }
    ]);
    apiMocks.getLlmSession.mockImplementation(sessionId => Promise.resolve({
      sessionId,
      documentId: "doc-1",
      title: sessionId === "session-1" ? "当前会话" : "目标会话",
      lastSnapshotText: "",
      lastSnapshotIsEmpty: true,
      lastHeadingId: "",
      lastHeadingText: "",
      messages: []
    }));
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-4",
          sessionId: "session-1",
          assistantMessageId: "assistant-4",
          provider: "stub-provider",
          model: "fake-gpt"
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort
      };
    });
    apiMocks.cancelLlmRequest.mockResolvedValue({
      documentId: "doc-1",
      requestId: "request-4",
      sessionId: "session-1",
      assistantMessageId: "assistant-4",
      status: "cancelled",
      assistantText: "",
      usage: null,
      finishReason: "",
      providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" },
      errorCode: "LLM_REQUEST_CANCELLED",
      startedTime: "",
      finishedTime: ""
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("切换前先发一条");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    await wrapper.vm.handleSessionClick("session-2");
    await flushPromises();

    expect(elementPlusMocks.confirm).toHaveBeenCalledWith(
      "当前回答仍在生成中，切换会话会停止本次回复。是否继续切换？",
      "切换会话",
      expect.objectContaining({
        confirmButtonText: "继续切换",
        cancelButtonText: "留在当前会话",
        type: "warning"
      })
    );
    expect(abort).toHaveBeenCalled();
    expect(apiMocks.cancelLlmRequest).toHaveBeenCalledWith("request-4", "doc-1");
    expect(elementPlusMocks.info).toHaveBeenCalledWith("已停止当前回复，正在切换会话");
    expect(wrapper.text()).toContain("当前会话：目标会话");
  });
});

function mountWorkbench(props = {}) {
  return mount(EditorAiWorkbench, {
    props: {
      documentTitle: "路线图.docx",
      runtimeContext: runtimeContext(),
      loading: false,
      closing: false,
      ...props
    }
  });
}

function runtimeContext(overrides = {}) {
  return {
    documentId: "doc-1",
    documentTitle: "路线图.docx",
    selectedText: "当前选区文本",
    hasEmptySelection: false,
    outlineTreeData: [{ id: "heading-1", text: "第一章", label: "第一章" }],
    activeHeadingId: "heading-1",
    activeHeadingNode: { id: "heading-1", text: "第一章", label: "第一章" },
    bridgeStatusMessage: "桥接已就绪",
    bridgeReady: true,
    ...overrides
  };
}

function deferred() {
  let resolve;
  const promise = new Promise(res => {
    resolve = res;
  });
  return { promise, resolve };
}
