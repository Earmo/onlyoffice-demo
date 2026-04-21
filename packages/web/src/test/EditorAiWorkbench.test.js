import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "./helpers";

const apiMocks = vi.hoisted(() => ({
  getLlmCapability: vi.fn(),
  listLlmSessions: vi.fn(),
  createLlmSession: vi.fn(),
  getLlmSession: vi.fn(),
  sendLlmMessage: vi.fn(),
  getLlmRequest: vi.fn(),
  cancelLlmRequest: vi.fn()
}));

vi.mock("../components/editor/editorAiApi.js", () => apiMocks);

import EditorAiWorkbench from "../components/editor/EditorAiWorkbench.vue";

describe("EditorAiWorkbench", () => {
  beforeEach(() => {
    Object.values(apiMocks).forEach(mock => mock.mockReset());
    apiMocks.getLlmCapability.mockResolvedValue({
      documentId: "doc-1",
      llmAvailable: true,
      disabledReason: null,
      provider: "openai-compatible",
      model: "fake-gpt",
      supportsUpstreamCancel: false
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
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("应展示 capability-disabled 和 disabledReason", async () => {
    apiMocks.getLlmCapability.mockResolvedValueOnce({
      documentId: "doc-1",
      llmAvailable: false,
      disabledReason: "LLM_DISABLED",
      provider: "openai-compatible",
      model: "fake-gpt",
      supportsUpstreamCancel: false
    });

    const wrapper = mount(EditorAiWorkbench, {
      props: {
        documentTitle: "路线图.docx",
        runtimeContext: runtimeContext(),
        loading: false,
        closing: false
      }
    });
    await flushPromises();

    expect(wrapper.find(".capability-disabled").exists()).toBe(true);
    expect(wrapper.text()).toContain("LLM_DISABLED");
    expect(wrapper.text()).toContain("llmAvailable=false");
  });

  it("应忽略自动建会话 stale response", async () => {
    const firstSession = deferred();
    const secondSession = deferred();
    apiMocks.getLlmCapability.mockResolvedValue({
      documentId: "doc-1",
      llmAvailable: true,
      disabledReason: null,
      provider: "openai-compatible",
      model: "fake-gpt",
      supportsUpstreamCancel: false
    });
    apiMocks.createLlmSession
      .mockImplementationOnce(() => firstSession.promise)
      .mockImplementationOnce(() => secondSession.promise);
    apiMocks.listLlmSessions.mockResolvedValue([]);

    const wrapper = mount(EditorAiWorkbench, {
      props: {
        documentTitle: "路线图.docx",
        runtimeContext: runtimeContext({ documentId: "doc-1" }),
        loading: false,
        closing: false
      }
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

  it("应在 in_progress 轮询到 completed", async () => {
    apiMocks.sendLlmMessage.mockResolvedValueOnce({
      documentId: "doc-1",
      requestId: "request-1",
      sessionId: "session-1",
      assistantMessageId: "assistant-1",
      status: "in_progress",
      assistantText: "",
      usage: null,
      finishReason: "",
      providerResponseMeta: {},
      errorCode: "",
      startedTime: "",
      finishedTime: null
    });
    apiMocks.getLlmRequest.mockResolvedValueOnce({
      documentId: "doc-1",
      requestId: "request-1",
      sessionId: "session-1",
      assistantMessageId: "assistant-1",
      status: "completed",
      assistantText: "完成回复",
      usage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
      finishReason: "stop",
      providerResponseMeta: { model: "fake-gpt" },
      errorCode: "",
      startedTime: "",
      finishedTime: ""
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find(".composer-input").setValue("帮我总结这一段");
    await wrapper.find(".primary-button").trigger("click");
    await flushPromises();

    await new Promise(resolve => setTimeout(resolve, 1700));
    await flushPromises();

    expect(apiMocks.getLlmRequest).toHaveBeenCalledWith("request-1", "doc-1");
    expect(wrapper.text()).toContain("完成回复");
    expect(wrapper.text()).toContain("promptTokens: 10");
  });

  it("应在取消后显示请求已取消", async () => {
    apiMocks.sendLlmMessage.mockResolvedValueOnce({
      documentId: "doc-1",
      requestId: "request-2",
      sessionId: "session-1",
      assistantMessageId: "assistant-2",
      status: "in_progress",
      assistantText: "",
      usage: null,
      finishReason: "",
      providerResponseMeta: {},
      errorCode: "",
      startedTime: "",
      finishedTime: null
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
      providerResponseMeta: {},
      errorCode: "LLM_REQUEST_CANCELLED",
      startedTime: "",
      finishedTime: ""
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find(".composer-input").setValue("需要取消");
    await wrapper.find(".primary-button").trigger("click");
    await flushPromises();

    const cancelButton = wrapper.findAll("button").find(button => button.text().includes("取消发送"));
    await cancelButton.trigger("click");
    await flushPromises();

    expect(apiMocks.cancelLlmRequest).toHaveBeenCalledWith("request-2", "doc-1");
    expect(wrapper.text()).toContain("请求已取消");
    expect(wrapper.text()).toContain("LLM_REQUEST_CANCELLED");
  });

  it("应在 LLM_SESSION_FORBIDDEN 时自动回退到新会话", async () => {
    apiMocks.listLlmSessions.mockResolvedValue([
      { sessionId: "session-1", title: "当前会话", updatedTime: "2026-04-21T00:00:00Z" },
      { sessionId: "session-old", title: "旧线程", updatedTime: "2026-04-20T00:00:00Z" }
    ]);
    apiMocks.getLlmSession.mockRejectedValueOnce(apiError("LLM_SESSION_FORBIDDEN", "当前用户无权访问该对话会话。"));
    apiMocks.createLlmSession
      .mockResolvedValueOnce({
        sessionId: "session-1",
        documentId: "doc-1",
        title: "当前会话",
        lastSnapshotText: "",
        lastSnapshotIsEmpty: true,
        lastHeadingId: "",
        lastHeadingText: "",
        messages: []
      })
      .mockResolvedValueOnce({
        sessionId: "session-fallback",
        documentId: "doc-1",
        title: "回退新会话",
        lastSnapshotText: "",
        lastSnapshotIsEmpty: true,
        lastHeadingId: "",
        lastHeadingText: "",
        messages: []
      });

    const wrapper = mountWorkbench();
    await flushPromises();

    const oldSessionButton = wrapper.findAll(".session-item").find(button => button.text().includes("旧线程"));
    await oldSessionButton.trigger("click");
    await flushPromises();

    expect(apiMocks.createLlmSession).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("LLM_SESSION_FORBIDDEN");
    expect(wrapper.text()).toContain("当前会话：回退新会话");
  });

  it("应展示 errorCode 并在重试确认后发送 retryConfirmed: true", async () => {
    apiMocks.sendLlmMessage
      .mockResolvedValueOnce({
        documentId: "doc-1",
        requestId: "request-3",
        sessionId: "session-1",
        assistantMessageId: "assistant-3",
        status: "failed",
        assistantText: "",
        usage: null,
        finishReason: "",
        providerResponseMeta: {},
        errorCode: "LLM_PROVIDER_BAD_REQUEST",
        startedTime: "",
        finishedTime: ""
      })
      .mockResolvedValueOnce({
        documentId: "doc-1",
        requestId: "request-4",
        sessionId: "session-1",
        assistantMessageId: "assistant-4",
        status: "completed",
        assistantText: "重试成功",
        usage: { promptTokens: 1, completionTokens: 2, totalTokens: 3 },
        finishReason: "stop",
        providerResponseMeta: { model: "fake-gpt" },
        errorCode: "",
        startedTime: "",
        finishedTime: ""
      });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find(".composer-input").setValue("需要失败后重试");
    await wrapper.find(".primary-button").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("LLM_PROVIDER_BAD_REQUEST");

    const retryButton = wrapper.findAll("button").find(button => button.text().includes("重试"));
    await retryButton.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("retryConfirmed: true");

    const confirmButton = wrapper.findAll("button").find(button => button.text().includes("确认重试"));
    await confirmButton.trigger("click");
    await flushPromises();

    expect(apiMocks.sendLlmMessage.mock.calls[1][0]).toEqual(expect.objectContaining({ retryConfirmed: true }));
    expect(wrapper.text()).toContain("重试成功");
  });

  it("应在发送接口抛错时把 pending 条目标记为 failed 并展示错误卡片", async () => {
    apiMocks.sendLlmMessage.mockRejectedValueOnce(apiError("NETWORK_ERROR", "网络断开"));

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find(".composer-input").setValue("模拟网络失败");
    await wrapper.find(".primary-button").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("NETWORK_ERROR");
    expect(wrapper.text()).toContain("网络断开");
    expect(wrapper.find(".thread-error-card").exists()).toBe(true);
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

function apiError(errorCode, message) {
  const error = new Error(message);
  error.errorCode = errorCode;
  return error;
}
