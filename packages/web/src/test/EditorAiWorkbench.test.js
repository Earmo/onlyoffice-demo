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
  cancelLlmRequest: vi.fn(),
  setLlmActiveVariant: vi.fn()
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
import { useWriteBackStore } from "../stores/writeBackStore";

describe("EditorAiWorkbench", () => {
  beforeEach(() => {
    Object.values(apiMocks).forEach(mock => mock.mockReset());
    Object.values(elementPlusMocks).forEach(mock => mock.mockReset());
    useWriteBackStore().reset();
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
    apiMocks.setLlmActiveVariant.mockResolvedValue({
      activeVariantIndex: 0
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
        handlers.onReasoningDelta?.({ requestId: "request-1", reasoningText: "先结合选区识别主题，" });
        handlers.onDelta?.({ requestId: "request-1", delta: "流式" });
        handlers.onReasoningDelta?.({ requestId: "request-1", reasoningText: "再给出结构化建议。" });
        handlers.onDelta?.({ requestId: "request-1", delta: "回复" });
        handlers.onMeta?.({
          requestId: "request-1",
          usage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
          finishReason: "stop",
          providerResponseMeta: {
            provider: "stub-provider",
            model: "fake-gpt",
            reasoningContent: "先结合选区识别主题，再给出结构化建议。"
          }
        });
        handlers.onCompleted?.({
          requestId: "request-1",
          sessionId: "session-1",
          assistantMessageId: "assistant-1",
          assistantText: "流式回复",
          usage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
          finishReason: "stop",
          providerResponseMeta: {
            provider: "stub-provider",
            model: "fake-gpt",
            reasoningContent: "先结合选区识别主题，再给出结构化建议。"
          }
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

    expect(wrapper.text()).toContain("新会话");
    expect(wrapper.text()).not.toContain("旧会话");
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
    expect(wrapper.text()).toContain("深度思考");
    expect(wrapper.text()).toContain("先结合选区识别主题，再给出结构化建议。");
    expect(wrapper.text()).toContain("promptTokens: 10");
    expect(wrapper.text()).toContain("provider: stub-provider");
    const entry = wrapper.find(".thread-entry").element;
    const reasoningPanel = wrapper.find('[data-testid="reasoning-panel"]');
    const assistantAnswer = wrapper.find('[data-testid="assistant-answer"]');
    expect(reasoningPanel.exists()).toBe(true);
    expect(reasoningPanel.element.open).toBe(false);
    const testNodes = [...entry.querySelectorAll("[data-testid]")];
    expect(testNodes.indexOf(reasoningPanel.element)).toBeLessThan(testNodes.indexOf(assistantAnswer.element));
  });

  it("应在首轮 request-started 返回标题后刷新当前会话名", async () => {
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-title",
          sessionId: "session-1",
          sessionTitle: "总结这一段",
          assistantMessageId: "assistant-title",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onCompleted?.({
          requestId: "request-title",
          sessionId: "session-1",
          assistantMessageId: "assistant-title",
          assistantText: "完成",
          usage: null,
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

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("帮我总结这一段");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("总结这一段");
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

  it("应渲染并清洗 reasoning Markdown", async () => {
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-md",
          sessionId: "session-1",
          assistantMessageId: "assistant-md",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onReasoningDelta?.({
          requestId: "request-md",
          reasoningText: "## 标题\n<script>alert(1)</script>\n<a href=\"javascript:alert(1)\">链接</a>"
        });
        handlers.onDelta?.({ requestId: "request-md", delta: "回答" });
        handlers.onCompleted?.({
          requestId: "request-md",
          sessionId: "session-1",
          assistantMessageId: "assistant-md",
          assistantText: "回答",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("检查 Markdown");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    const reasoningPanel = wrapper.find('[data-testid="reasoning-panel"]');
    expect(reasoningPanel.html()).toContain("<h2>标题</h2>");
    expect(reasoningPanel.html()).not.toContain("<script>");
    expect(reasoningPanel.html()).not.toContain("javascript:");
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

  it("应在取消终态缺少 reasoning 时保留已收到的半成品", async () => {
    const abort = vi.fn(() => Promise.resolve());
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-partial-cancel",
          sessionId: "session-1",
          assistantMessageId: "assistant-partial-cancel",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onReasoningDelta?.({ requestId: "request-partial-cancel", reasoningText: "已收到的思考" });
        handlers.onDelta?.({ requestId: "request-partial-cancel", delta: "半截回答" });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort
      };
    });
    apiMocks.cancelLlmRequest.mockResolvedValueOnce({
      documentId: "doc-1",
      requestId: "request-partial-cancel",
      sessionId: "session-1",
      assistantMessageId: "assistant-partial-cancel",
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

    await wrapper.find("textarea").setValue("部分取消");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();
    await wrapper.find('button[title="取消发送"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("半截回答");
    expect(wrapper.text()).toContain("已收到的思考");
    expect(wrapper.text()).toContain("LLM_REQUEST_CANCELLED");
  });

  it("应在失败终态缺少 reasoning 时保留已收到的半成品", async () => {
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-partial-failed",
          sessionId: "session-1",
          assistantMessageId: "assistant-partial-failed",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onReasoningDelta?.({ requestId: "request-partial-failed", reasoningText: "失败前思考" });
        handlers.onDelta?.({ requestId: "request-partial-failed", delta: "失败前回答" });
        handlers.onError?.({
          requestId: "request-partial-failed",
          sessionId: "session-1",
          assistantMessageId: "assistant-partial-failed",
          errorCode: "LLM_PROVIDER_UPSTREAM_ERROR",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("部分失败");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("失败前回答");
    expect(wrapper.text()).toContain("失败前思考");
    expect(wrapper.text()).toContain("LLM_PROVIDER_UPSTREAM_ERROR");
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
        handlers.onReasoningDelta?.({ requestId: "request-3", reasoningText: "临时思考" });
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
      providerResponseMeta: { provider: "stub-provider", model: "fake-gpt", reasoningContent: "最终思考" },
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
    expect(wrapper.text()).toContain("最终思考");
  });

  it("应在 terminal 缺少 reasoning 时保留 streamed reasoning", async () => {
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          requestId: "request-streamed-reasoning",
          sessionId: "session-1",
          assistantMessageId: "assistant-streamed-reasoning",
          provider: "stub-provider",
          model: "fake-gpt",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
        handlers.onReasoningDelta?.({ requestId: "request-streamed-reasoning", reasoningText: "流式思考" });
        handlers.onDelta?.({ requestId: "request-streamed-reasoning", delta: "回答" });
        handlers.onCompleted?.({
          requestId: "request-streamed-reasoning",
          sessionId: "session-1",
          assistantMessageId: "assistant-streamed-reasoning",
          assistantText: "回答",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.find("textarea").setValue("terminal 空 reasoning");
    await wrapper.find('button[title="发送问题"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("流式思考");
    expect(wrapper.text()).toContain("回答");
  });

  it("应从历史消息恢复 reasoning 并保持在正文前", async () => {
    apiMocks.listLlmSessions.mockResolvedValue([
      {
        sessionId: "session-history",
        documentId: "doc-1",
        title: "历史会话",
        updatedTime: "2026-04-27T10:00:00Z"
      }
    ]);
    apiMocks.getLlmSession.mockResolvedValue({
      sessionId: "session-history",
      documentId: "doc-1",
      title: "历史会话",
      lastSnapshotText: "历史选区",
      lastSnapshotIsEmpty: false,
      lastHeadingId: "heading-1",
      lastHeadingText: "第一章",
      messages: [
        {
          role: "user",
          messageId: "user-history",
          question: "历史问题",
          snapshotText: "历史选区",
          snapshotEmptySelection: false,
          includeHeading: true,
          headingId: "heading-1",
          headingText: "第一章"
        },
        {
          role: "assistant",
          messageId: "assistant-history",
          status: "completed",
          assistantText: "历史回答",
          providerResponseMeta: {
            provider: "stub-provider",
            model: "fake-gpt",
            reasoningContent: "历史深度思考"
          }
        }
      ]
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.text()).toContain("历史会话");
    expect(wrapper.text()).toContain("历史深度思考");
    expect(wrapper.text()).toContain("历史回答");
    const entry = wrapper.find(".thread-entry").element;
    const reasoningPanel = wrapper.find('[data-testid="reasoning-panel"]');
    const assistantAnswer = wrapper.find('[data-testid="assistant-answer"]');
    expect(reasoningPanel.element.open).toBe(false);
    const testNodes = [...entry.querySelectorAll("[data-testid]")];
    expect(testNodes.indexOf(reasoningPanel.element)).toBeLessThan(testNodes.indexOf(assistantAnswer.element));
  });

  it("应从历史 variants 只渲染 active variant", async () => {
    apiMocks.listLlmSessions.mockResolvedValue([
      {
        sessionId: "session-variants",
        documentId: "doc-1",
        title: "多版本会话",
        updatedTime: "2026-04-28T10:00:00Z"
      }
    ]);
    apiMocks.getLlmSession.mockResolvedValue({
      sessionId: "session-variants",
      documentId: "doc-1",
      title: "多版本会话",
      lastSnapshotText: "历史选区",
      lastSnapshotIsEmpty: false,
      lastHeadingId: "heading-1",
      lastHeadingText: "第一章",
      messages: [
        {
          role: "user",
          messageId: "user-variants",
          question: "历史问题",
          snapshotText: "历史选区",
          snapshotEmptySelection: false,
          includeHeading: true,
          headingId: "heading-1",
          headingText: "第一章"
        },
        {
          role: "assistant",
          messageId: "assistant-variants",
          activeVariantIndex: 1,
          variants: [
            {
              variantId: "variant-0",
              variantIndex: 0,
              status: "completed",
              assistantText: "旧版本回答",
              providerResponseMeta: {
                provider: "stub-provider",
                model: "fake-gpt",
                reasoningContent: "旧版本思考"
              }
            },
            {
              variantId: "variant-1",
              variantIndex: 1,
              status: "completed",
              assistantText: "当前版本回答",
              finishReason: "stop",
              usage: { promptTokens: 2, completionTokens: 3, totalTokens: 5 },
              providerResponseMeta: {
                provider: "stub-provider",
                model: "fake-gpt-2",
                reasoningContent: "当前版本思考"
              }
            }
          ]
        }
      ]
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.findAll(".thread-entry")).toHaveLength(1);
    expect(wrapper.text()).toContain("当前版本回答");
    expect(wrapper.text()).toContain("当前版本思考");
    expect(wrapper.text()).toContain("provider: stub-provider");
    expect(wrapper.text()).toContain("model: fake-gpt-2");
    expect(wrapper.text()).toContain("promptTokens: 2");
    expect(wrapper.text()).not.toContain("旧版本回答");
    expect(wrapper.text()).not.toContain("旧版本思考");
  });

  it("重新生成应写入同一 entry 的新 variant 而不是追加纵向消息", async () => {
    mockSessionWithVariants();
    apiMocks.startLlmMessageStream.mockImplementation((payload, handlers = {}) => {
      expect(payload).toEqual(expect.objectContaining({
        regenerateAssistantMessageId: "assistant-variants",
        retryConfirmed: true
      }));
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-1",
          variantIndex: 1,
          activeVariantIndex: 1,
          provider: "stub-provider",
          model: "fake-gpt"
        });
        handlers.onReasoningDelta?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-1",
          variantIndex: 1,
          reasoningText: "新版本思考"
        });
        handlers.onDelta?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-1",
          variantIndex: 1,
          delta: "新版本"
        });
        handlers.onDelta?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-1",
          variantIndex: 1,
          delta: "回答"
        });
        handlers.onCompleted?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-1",
          variantIndex: 1,
          activeVariantIndex: 1,
          status: "completed",
          assistantText: "新版本回答",
          providerResponseMeta: {
            provider: "stub-provider",
            model: "fake-gpt",
            reasoningContent: "新版本思考"
          }
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.findAll(".message-actions button")[1].trigger("click");
    await flushPromises();

    expect(wrapper.findAll(".thread-entry")).toHaveLength(1);
    expect(wrapper.text()).toContain("新版本回答");
    expect(wrapper.text()).toContain("新版本思考");
    expect(wrapper.text()).not.toContain("初始版本回答");
  });

  it("重新生成取消后应恢复旧 completed active variant 并保留取消状态", async () => {
    mockSessionWithVariants();
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-cancel-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-cancelled",
          variantIndex: 1,
          activeVariantIndex: 1
        });
        handlers.onDelta?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-cancel-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-cancelled",
          variantIndex: 1,
          delta: "半截新版本"
        });
        handlers.onCancelled?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-cancel-regenerate",
          assistantMessageId: "assistant-variants",
          variantId: "variant-cancelled",
          variantIndex: 1,
          status: "cancelled",
          assistantText: "半截新版本",
          errorCode: "LLM_REQUEST_CANCELLED"
        });
      });
      return {
        ready: Promise.resolve(),
        done: Promise.resolve(),
        abort: vi.fn(() => Promise.resolve())
      };
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.findAll(".message-actions button")[1].trigger("click");
    await flushPromises();

    expect(wrapper.findAll(".thread-entry")).toHaveLength(1);
    expect(wrapper.text()).toContain("初始版本回答");
    expect(wrapper.text()).not.toContain("半截新版本");
    expect(wrapper.vm.conversationEntries[0].variants).toEqual(expect.arrayContaining([
      expect.objectContaining({
        variantIndex: 1,
        status: "cancelled",
        errorCode: "LLM_REQUEST_CANCELLED"
      })
    ]));
  });

  it("重新生成断流回查应按 variant identity 合并并保留 terminal reasoning", async () => {
    mockSessionWithVariants();
    apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
      queueMicrotask(() => {
        handlers.onStarted?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-reconcile-variant",
          assistantMessageId: "assistant-variants",
          variantId: "variant-reconciled",
          variantIndex: 1,
          activeVariantIndex: 1
        });
        handlers.onDelta?.({
          documentId: "doc-1",
          sessionId: "session-variants",
          requestId: "request-reconcile-variant",
          assistantMessageId: "assistant-variants",
          variantId: "variant-reconciled",
          variantIndex: 1,
          delta: "临时"
        });
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
      sessionId: "session-variants",
      requestId: "request-reconcile-variant",
      assistantMessageId: "assistant-variants",
      variantId: "variant-reconciled",
      variantIndex: 1,
      activeVariantIndex: 1,
      status: "completed",
      assistantText: "回查完成版本",
      providerResponseMeta: {
        provider: "stub-provider",
        model: "fake-gpt",
        reasoningContent: "回查终态思考"
      }
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    await wrapper.findAll(".message-actions button")[1].trigger("click");
    await flushPromises();

    expect(apiMocks.getLlmRequest).toHaveBeenCalledWith("request-reconcile-variant", "doc-1");
    expect(wrapper.findAll(".thread-entry")).toHaveLength(1);
    expect(wrapper.text()).toContain("回查完成版本");
    expect(wrapper.text()).toContain("回查终态思考");
    expect(wrapper.text()).not.toContain("初始版本回答");
  });

  it("多版本控件应持久化 active 切换并驱动复制和写回", async () => {
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText }
    });
    mockSessionWithVariants({
      activeVariantIndex: 1,
      variants: [
        {
          variantId: "variant-0",
          variantIndex: 0,
          status: "completed",
          assistantText: "第一个版本回答",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        },
        {
          variantId: "variant-1",
          variantIndex: 1,
          status: "completed",
          assistantText: "第二个版本回答",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt-2" }
        }
      ]
    });
    apiMocks.setLlmActiveVariant.mockResolvedValueOnce({ activeVariantIndex: 0 });

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.get('[data-testid="variant-counter"]').text()).toBe("2/2");
    await wrapper.get('[data-testid="variant-prev"]').trigger("click");
    await flushPromises();

    expect(apiMocks.setLlmActiveVariant).toHaveBeenCalledWith(expect.objectContaining({
      documentId: "doc-1",
      sessionId: "session-variants",
      assistantMessageId: "assistant-variants",
      variantIndex: 0
    }));
    expect(wrapper.get('[data-testid="variant-counter"]').text()).toBe("1/2");
    expect(wrapper.text()).toContain("第一个版本回答");
    expect(wrapper.text()).not.toContain("第二个版本回答");

    await wrapper.get('[data-testid="copy-active-variant"]').trigger("click");
    await flushPromises();
    expect(writeText).toHaveBeenCalledWith("第一个版本回答");

    await wrapper.find('button[title="将回复写入文档"]').trigger("click");
    await flushPromises();
    expect(wrapper.vm.writeBackHtml).toContain("第一个版本回答");
    expect(wrapper.vm.writeBackHtml).not.toContain("第二个版本回答");
  });

  it("active variant 生成中时应禁用复制和写回", async () => {
    mockSessionWithVariants({
      variants: [
        {
          variantId: "variant-0",
          variantIndex: 0,
          status: "in_progress",
          assistantText: "",
          streamingText: "生成中的半截回答",
          providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
        }
      ]
    });

    const wrapper = mountWorkbench();
    await flushPromises();

    expect(wrapper.get('[data-testid="copy-active-variant"]').attributes("disabled")).toBeDefined();
    expect(wrapper.find('button[title="将回复写入文档"]').exists()).toBe(false);
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
    expect(wrapper.text()).toContain("目标会话");
  });

  describe("写入文档功能（Pinia store 反馈模式）", () => {
    it("completed 条目显示「写入文档」按钮", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply();

      expect(wrapper.find('button[title="将回复写入文档"]').exists()).toBe(true);
    });

    it("点击后打开确认对话框并生成净化后的 HTML 预览", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply({
        assistantText: "## 标题\n\n<script>alert(1)</script>\n\n<a href=\"javascript:alert(1)\">链接</a>"
      });

      await wrapper.find('button[title="将回复写入文档"]').trigger("click");
      await flushPromises();

      expect(wrapper.vm.writeBackDialogVisible).toBe(true);
      expect(wrapper.vm.writeBackHtml).toContain("<h2>标题</h2>");
      expect(wrapper.vm.writeBackHtml).not.toContain("<script>");
      expect(wrapper.vm.writeBackHtml).not.toContain("javascript:");
    });

    it("openWriteBackDialog 冻结 writeBackHasSelection 快照", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply({
        runtimeContext: runtimeContext({ hasEmptySelection: false })
      });

      await wrapper.vm.openWriteBackDialog(wrapper.vm.conversationEntries[0]);
      await wrapper.setProps({
        runtimeContext: runtimeContext({ hasEmptySelection: true })
      });
      await flushPromises();

      expect(wrapper.vm.writeBackHasSelection).toBe(true);
    });

    it("无选区时「替换当前选区」禁用逻辑基于冻结 writeBackHasSelection", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply({
        runtimeContext: runtimeContext({ hasEmptySelection: true })
      });

      await wrapper.vm.openWriteBackDialog(wrapper.vm.conversationEntries[0]);
      await flushPromises();

      expect(wrapper.vm.writeBackHasSelection).toBe(false);
      expect(wrapper.vm.writeBackMode).toBe("cursor");
      expect(document.body.textContent).toContain("当前无选区");
    });

    it("confirmWriteBack 设 store.status=loading 并 emit insert-html（无回调）", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply();

      await wrapper.vm.openWriteBackDialog(wrapper.vm.conversationEntries[0]);
      await wrapper.vm.confirmWriteBack();
      await flushPromises();

      expect(wrapper.vm.writeBackStore.status).toBe("loading");
      const emitted = wrapper.emitted("insert-html");
      expect(emitted).toBeTruthy();
      expect(emitted[0][0]).toMatchObject({ html: expect.any(String) });
      expect(emitted[0][0].onSuccess).toBeUndefined();
      expect(emitted[0][0].onError).toBeUndefined();
    });

    it("store.status=success 时提示成功并关闭对话框", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply();

      await wrapper.vm.openWriteBackDialog(wrapper.vm.conversationEntries[0]);
      await flushPromises();
      wrapper.vm.writeBackStore.status = "success";
      await wrapper.vm.$nextTick();
      await flushPromises();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(elementPlusMocks.success).toHaveBeenCalledWith("已写入文档");
      expect(wrapper.vm.writeBackStore.status).toBe("idle");
    });

    it("store.status=error 时提示错误并保持对话框开启", async () => {
      const wrapper = await mountWorkbenchWithCompletedReply();

      await wrapper.vm.openWriteBackDialog(wrapper.vm.conversationEntries[0]);
      await wrapper.vm.confirmWriteBack();
      await flushPromises();
      wrapper.vm.writeBackStore.errorMsg = "写入超时";
      wrapper.vm.writeBackStore.status = "error";
      await wrapper.vm.$nextTick();
      await flushPromises();

      expect(elementPlusMocks.error).toHaveBeenCalledWith("写入超时");
      expect(wrapper.vm.writeBackDialogVisible).toBe(true);
      expect(wrapper.vm.writeBackStore.status).toBe("idle");
    });
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

async function mountWorkbenchWithCompletedReply(options = {}) {
  const assistantText = options.assistantText || "流式回复";
  apiMocks.startLlmMessageStream.mockImplementation((_payload, handlers = {}) => {
    queueMicrotask(() => {
      handlers.onStarted?.({
        documentId: "doc-1",
        requestId: "request-writeback",
        sessionId: "session-1",
        assistantMessageId: "assistant-writeback",
        provider: "stub-provider",
        model: "fake-gpt",
        providerResponseMeta: { provider: "stub-provider", model: "fake-gpt" }
      });
      handlers.onCompleted?.({
        requestId: "request-writeback",
        sessionId: "session-1",
        assistantMessageId: "assistant-writeback",
        assistantText,
        usage: null,
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

  const wrapper = mountWorkbench({
    runtimeContext: options.runtimeContext || runtimeContext({ hasEmptySelection: false })
  });
  await flushPromises();
  await wrapper.find("textarea").setValue("测试写回");
  await wrapper.find('button[title="发送问题"]').trigger("click");
  await flushPromises();
  return wrapper;
}

function mockSessionWithVariants(options = {}) {
  apiMocks.listLlmSessions.mockResolvedValue([
    {
      sessionId: "session-variants",
      documentId: "doc-1",
      title: "多版本会话",
      updatedTime: "2026-04-28T10:00:00Z"
    }
  ]);
  apiMocks.getLlmSession.mockResolvedValue({
    sessionId: "session-variants",
    documentId: "doc-1",
    title: "多版本会话",
    lastSnapshotText: "历史选区",
    lastSnapshotIsEmpty: false,
    lastHeadingId: "heading-1",
    lastHeadingText: "第一章",
    messages: [
      {
        role: "user",
        messageId: "user-variants",
        question: "历史问题",
        snapshotText: "历史选区",
        snapshotEmptySelection: false,
        includeHeading: true,
        headingId: "heading-1",
        headingText: "第一章"
      },
      {
        role: "assistant",
        messageId: "assistant-variants",
        activeVariantIndex: options.activeVariantIndex ?? 0,
        variants: options.variants || [
          {
            variantId: "variant-0",
            variantIndex: 0,
            status: "completed",
            assistantText: "初始版本回答",
            providerResponseMeta: {
              provider: "stub-provider",
              model: "fake-gpt",
              reasoningContent: "初始版本思考"
            }
          }
        ]
      }
    ]
  });
}

function deferred() {
  let resolve;
  const promise = new Promise(res => {
    resolve = res;
  });
  return { promise, resolve };
}
