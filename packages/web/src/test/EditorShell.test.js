import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

// 这组 mock 把桥接层从组件测试中剥离出来，
// 让测试可以聚焦 EditorShell 的页面行为，而不是实际 ONLYOFFICE 运行时。
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
        { id: "heading-1", text: "一、项目背景", level: 1, styleName: "Heading 1", paragraphIndex: 3 },
        { id: "heading-2", text: "1.1 范围", level: 2, styleName: "Heading 2", paragraphIndex: 8 }
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

  it("应在编辑模式加载配置、展开控制台，并在显式关闭后卸载时不重复请求 close-session", async () => {
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
    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.find(".stage-edge-toggle").exists()).toBe(true);

    await wrapper.find(".stage-edge-toggle").trigger("click");
    await flushPromises();

    expect(wrapper.find(".stage-edge-toggle").exists()).toBe(false);
    expect(wrapper.text()).toContain("当前选区");
    expect(wrapper.text()).toContain("章节标题");
    expect(wrapper.text()).toContain("运行态 / 现有动作");

    await wrapper.vm.closeEditingSession();
    await flushPromises();
    wrapper.unmount();
    await flushPromises();

    const closeCalls = fetch.mock.calls.filter(call => String(call[0]).includes("/editing-sessions/close"));
    expect(closeCalls).toHaveLength(1);
  });

  it("应在 close-session 进行中复用同一个请求而不是重复发送", async () => {
    // 这个用例保护“重复点击离开 / 返回 / 切换”时不会发多次保存请求。
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

    await new Promise(r => setTimeout(r, 100));
    await flushPromises();

    expect(saveCallCount).toBe(1);

    resolveSaveRequest();
    const [firstPayload, secondPayload] = await Promise.all([firstClose, secondClose]);

    expect(firstPayload.state).toBe("saved");
    expect(secondPayload.state).toBe("saved");
  });

  it("应先等待后端保存完成，再发送 close-session", async () => {
    // close-session 顺序很关键：先 save，再 close，避免后端把最后一次修改落空。
    let resolveSaveRequest;
    let closeCallCount = 0;
    fetch.mockImplementation((url) => {
      const urlStr = String(url);
      if (urlStr.includes("/editor-config")) {
        return Promise.resolve(jsonResponse(editorConfigPayload("路线图.docx")));
      }
      if (urlStr.includes("/editing-sessions/heartbeat")) {
        return Promise.resolve(jsonResponse({}, { status: 204 }));
      }
      if (urlStr.includes("/save-status")) {
        return Promise.resolve(jsonResponse(saveStatusPayload()));
      }
      if (urlStr.includes("/save")) {
        return new Promise(resolve => {
          resolveSaveRequest = () => resolve(jsonResponse(saveStatusPayload({
            lastSavedTime: "2026-03-25T10:00:05Z"
          })));
        });
      }
      if (urlStr.includes("/editing-sessions/close")) {
        closeCallCount++;
        return Promise.resolve(jsonResponse(closedStatusPayload({
          lastSavedTime: "2026-03-25T10:00:05Z"
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

    const closing = wrapper.vm.closeEditingSession();

    await new Promise(r => setTimeout(r, 200));
    await flushPromises();
    expect(closeCallCount).toBe(0);

    resolveSaveRequest();
    const payload = await closing;
    expect(closeCallCount).toBe(1);
    expect(payload.lastSavedTime).toBe("2026-03-25T10:00:05Z");
  });

  it("应在只读预览模式下请求 readonly 配置且不展示控制台", async () => {
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
    expect(wrapper.text()).toContain("预览稿.docx");
    expect(wrapper.find(".floating-console").isVisible()).toBe(false);
    expect(wrapper.find(".stage-edge-toggle").exists()).toBe(false);
  });

  it("应能抓取当前选区并展示文本预览", async () => {
    // 这里验证的是宿主页按钮 -> 桥接调用 -> 面板渲染这一整条链路。
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

    const captureButton = wrapper.findAll("button").find(button => button.text().includes("抓取当前选区"));
    await captureButton.trigger("click");
    await flushPromises();

    expect(bridgeMocks.captureSelectedText).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("第一段选中文本");
  });

  it("应在没有选中文本时展示明确空态", async () => {
    bridgeMocks.captureSelectedText.mockResolvedValueOnce({ text: "", emptySelection: true });
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

    const captureButton = wrapper.findAll("button").find(button => button.text().includes("抓取当前选区"));
    await captureButton.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("当前没有选中文本");
  });

  it("应能刷新目录并展示标题列表", async () => {
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

    expect(bridgeMocks.refreshOutline).toHaveBeenCalled();
    expect(wrapper.text()).toContain("一、项目背景");
    expect(wrapper.text()).toContain("1.1 范围");
  });

  it("应在没有标题时展示目录空态", async () => {
    bridgeMocks.refreshOutline.mockResolvedValueOnce({ headings: [], emptyOutline: true });
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

    expect(wrapper.text()).toContain("当前文档还没有检测到标题段落");
  });

  it("应在点击标题后调用 jumpToHeading", async () => {
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

    // 直接点击渲染出的目录节点，更贴近真实用户操作，也能避开组件事件校验噪音。
    const outlineButton = wrapper.findAll(".custom-tree-node").find(button => button.text().includes("一、项目背景"));
    await outlineButton.trigger("click");
    await flushPromises();

    expect(bridgeMocks.jumpToHeading).toHaveBeenCalledWith(
      expect.objectContaining({ id: "heading-1", paragraphIndex: 3 })
    );
  });

  it("应支持折叠运行态与现有动作区域", async () => {
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

    expect(wrapper.text()).toContain("最近保存状态");

    // 当前页面里有多个“收起”按钮，最后一个才对应“运行态 / 现有动作”区块。
    const toggleButton = wrapper.findAll("button").filter(button => button.text().includes("收起")).at(-1);
    await toggleButton.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("运行态 / 现有动作");
    expect(wrapper.text()).not.toContain("最近保存状态");
  });
});

function editorConfigPayload(title, mode = "edit") {
  // 测试里只保留 EditorShell 真正依赖的最小 editor-config 形状。
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
    recentEvents: [
      {
        eventType: "save_succeeded",
        message: "最新修改已成功回写到共享存储。",
        eventTime: "2026-03-25T10:00:01Z"
      }
    ],
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
