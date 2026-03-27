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

  it("应在编辑模式加载配置、展示固定控制台并结束编辑会话", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse({
        documentServerUrl: "https://docs.example.test/",
        config: {
          document: {
            title: "路线图.docx"
          },
          editorConfig: {
            mode: "edit"
          }
        }
      }))
      .mockResolvedValueOnce(jsonResponse({
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
      }))
      .mockResolvedValueOnce(jsonResponse({
        documentId: "doc-1",
        state: "saved",
        message: "当前用户已离开编辑器，文档已退出活跃编辑状态。",
        lastCallbackStatus: 2,
        lastCallbackTime: "2026-03-25T10:00:00Z",
        lastSavedTime: "2026-03-25T10:00:01Z",
        recentEvents: []
      }));

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-1",
        documentTitle: "路线图.docx"
      }
    });
    await flushPromises();

    expect(fetch.mock.calls[0][0]).toContain("/api/documents/doc-1/editor-config?readonly=false");
    expect(wrapper.text()).toContain("编辑模式");
    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.text()).toContain("最新修改已成功回写到共享存储。");
    expect(wrapper.find(".docked-console").exists()).toBe(true);

    await wrapper.vm.closeEditingSession();
    await flushPromises();

    expect(fetch.mock.calls[2][0]).toContain("/api/documents/doc-1/editing-sessions/close");
  });

  it("应在只读预览模式下请求 readonly 配置且不展示控制台", async () => {
    fetch.mockResolvedValueOnce(jsonResponse({
      documentServerUrl: "https://docs.example.test/",
      config: {
        document: {
          title: "预览稿.docx"
        },
        editorConfig: {
          mode: "view"
        }
      }
    }));

    const wrapper = mount(EditorShell, {
      props: {
        documentId: "doc-2",
        documentTitle: "预览稿.docx",
        readonly: true,
        showConsole: false
      }
    });
    await flushPromises();

    expect(fetch.mock.calls[0][0]).toContain("/api/documents/doc-2/editor-config?readonly=true");
    expect(wrapper.text()).toContain("预览模式");
    expect(wrapper.find(".docked-console").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("编辑动作");
  });
});
