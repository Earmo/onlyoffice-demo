import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

const bridgeMocks = vi.hoisted(() => ({
  createOnlyofficeBridge: vi.fn(),
  waitForReady: vi.fn(),
  captureSelectedText: vi.fn(),
  refreshOutline: vi.fn(),
  jumpToHeading: vi.fn(),
  dispose: vi.fn()
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

vi.mock("../components/editor/EditorAiWorkbench.vue", () => ({
  default: {
    name: "EditorAiWorkbenchStub",
    props: ["documentTitle", "runtimeContext", "loading", "closing"],
    emits: ["capture-selection", "refresh-outline", "jump-to-heading", "insert-image"],
    template: `
      <div class="ai-workbench-stub">
        <p class="workbench-title">{{ documentTitle }}</p>
        <p class="runtime-document">{{ runtimeContext.documentId }}</p>
        <p class="runtime-selection">{{ runtimeContext.selectedText }}</p>
        <button class="emit-capture" @click="$emit('capture-selection')">capture</button>
        <button class="emit-refresh" @click="$emit('refresh-outline')">refresh</button>
        <button class="emit-jump" @click="$emit('jump-to-heading', { id: 'heading-1', text: '一、项目背景', paragraphIndex: 3 })">jump</button>
      </div>
    `
  }
}));

import EditorShell from "../components/editor/EditorShell.vue";

describe("EditorShell", () => {
  beforeEach(() => {
    fetch.mockReset();
    bridgeMocks.waitForReady.mockReset();
    bridgeMocks.captureSelectedText.mockReset();
    bridgeMocks.refreshOutline.mockReset();
    bridgeMocks.jumpToHeading.mockReset();
    bridgeMocks.dispose.mockReset();
    bridgeMocks.createOnlyofficeBridge.mockReset();

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
      dispose: bridgeMocks.dispose
    }));
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

    expect(String(fetch.mock.calls[0][0])).toContain("/api/documents/doc-1/editor-config?readonly=false");
    expect(wrapper.find(".stage-edge-toggle").exists()).toBe(true);

    await wrapper.find(".stage-edge-toggle").trigger("click");
    await flushPromises();

    expect(wrapper.find(".ai-workbench-stub").exists()).toBe(true);
    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.text()).toContain("doc-1");

    await wrapper.vm.closeEditingSession();
    await flushPromises();
    wrapper.unmount();
    await flushPromises();

    const closeCalls = fetch.mock.calls.filter(call => String(call[0]).includes("/editing-sessions/close"));
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

    await wrapper.find(".stage-edge-toggle").trigger("click");
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

  it("应在 close-session 进行中复用同一个请求而不是重复发送", async () => {
    let resolveSaveRequest;
    let saveCallCount = 0;

    fetch.mockImplementation((url) => {
      const urlStr = String(url);
      if (urlStr.includes("/editor-config")) {
        return Promise.resolve(jsonResponse(editorConfigPayload("路线图.docx")));
      }
      if (urlStr.includes("/save-status")) {
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
      if (urlStr.includes("/editing-sessions/close")) {
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

    expect(String(fetch.mock.calls[0][0])).toContain("/api/documents/doc-2/editor-config?readonly=true");
    expect(wrapper.find(".floating-console").isVisible()).toBe(false);
    expect(wrapper.find(".stage-edge-toggle").exists()).toBe(false);
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
