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

  it("应加载 editor-config 和保存状态并展示最近事件", async () => {
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
        documentServerUrl: "https://docs.example.test/",
        config: {
          document: {
            title: "路线图.docx"
          },
          editorConfig: {
            mode: "view"
          }
        }
      }))
      .mockResolvedValueOnce(jsonResponse({
        state: "saved",
        message: "最新修改已成功回写到共享存储。",
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

    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.text()).toContain("最新修改已成功回写到共享存储。");
    expect(wrapper.text()).toContain("save_succeeded");

    const readonlyButton = wrapper.findAll("button").find(button => button.text().includes("切换为只读"));
    await readonlyButton.trigger("click");
    await flushPromises();

    expect(fetch.mock.calls[2][0]).toContain("/api/documents/doc-1/editor-config?readonly=true");
  });
});
