import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

vi.mock("@onlyoffice/document-editor-vue", () => ({
  DocumentEditor: {
    name: "OnlyofficeDocumentEditorStub",
    props: ["documentServerUrl", "config"],
    template: "<div class='onlyoffice-stub'>{{ config?.document?.title }}</div>"
  }
}));

import EditorShell from "../components/editor/EditorShell.vue";

describe("EditorShell", () => {
  beforeEach(() => {
    fetch.mockReset();
  });

  it("应在编辑模式加载配置、展开控制台，并在显式关闭后卸载时不重复请求 close-session", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(editorConfigPayload("路线图.docx")))  // 1. editor-config
      .mockResolvedValueOnce(jsonResponse(saveStatusPayload()))                 // 2. 初始 save-status
      .mockResolvedValueOnce(jsonResponse(saveStatusPayload()))                 // 3. closeSession 中 waitForSaveCompleted 轮询
      .mockResolvedValueOnce(jsonResponse(closedStatusPayload()));              // 4. close-session

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
    expect(wrapper.text()).toContain("编辑模式");

    await wrapper.vm.closeEditingSession();
    await flushPromises();
    wrapper.unmount();
    await flushPromises();

    const closeCalls = fetch.mock.calls.filter(call => String(call[0]).includes("/editing-sessions/close"));
    expect(closeCalls).toHaveLength(1);
  });

  it("应在 close-session 进行中复用同一个请求而不是重复发送", async () => {
    let resolveCloseRequest;
    let closeCallCount = 0;

    // 使用 URL 路由式 mock，让 waitForSaveCompleted 的轮询和 close 请求各走各的分支。
    fetch.mockImplementation((url) => {
      const urlStr = String(url);
      if (urlStr.includes("/editor-config")) {
        return Promise.resolve(jsonResponse(editorConfigPayload("路线图.docx")));
      }
      if (urlStr.includes("/save-status")) {
        return Promise.resolve(jsonResponse(saveStatusPayload()));
      }
      if (urlStr.includes("/editing-sessions/close")) {
        closeCallCount++;
        return new Promise(resolve => {
          resolveCloseRequest = () => resolve(jsonResponse(closedStatusPayload()));
        });
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

    // 等待 waitForSaveCompleted 内部的 setTimeout(500ms) 过期 + fetch 完成 + close 请求发出
    await new Promise(r => setTimeout(r, 700));
    await flushPromises();

    expect(closeCallCount).toBe(1);

    resolveCloseRequest();
    const [firstPayload, secondPayload] = await Promise.all([firstClose, secondClose]);

    expect(firstPayload.state).toBe("saved");
    expect(secondPayload.state).toBe("saved");
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

function saveStatusPayload() {
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
    ]
  };
}

function closedStatusPayload() {
  return {
    documentId: "doc-1",
    state: "saved",
    message: "当前用户已离开编辑器，文档已退出活跃编辑状态。",
    lastCallbackStatus: 2,
    lastCallbackTime: "2026-03-25T10:00:00Z",
    lastSavedTime: "2026-03-25T10:00:01Z",
    recentEvents: []
  };
}
