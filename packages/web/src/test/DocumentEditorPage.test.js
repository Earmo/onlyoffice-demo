import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

let routeState = { params: { documentId: "doc-1" }, query: {} };
const routerPush = vi.fn();

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush
  })
}));

vi.mock("../components/editor/EditorShell.vue", () => ({
  default: {
    name: "EditorShellStub",
    props: ["documentId", "documentTitle"],
    template: "<div class='editor-shell-stub'>{{ documentTitle }}::{{ documentId }}</div>"
  }
}));

import DocumentEditorPage from "../pages/DocumentEditorPage.vue";

describe("DocumentEditorPage", () => {
  beforeEach(() => {
    routeState = { params: { documentId: "doc-1" }, query: {} };
    routerPush.mockReset();
    routerPush.mockResolvedValue(undefined);
    window.confirm = vi.fn();
  });

  it("应加载编辑页并支持返回工作台与切换文档确认", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse({
        documentId: "doc-1",
        title: "路线图.docx",
        status: "saved",
        lastSavedTime: "2026-03-25T10:00:00Z"
      }))
      .mockResolvedValueOnce(jsonResponse({
        documents: [
          {
            documentId: "doc-1",
            title: "路线图.docx",
            lastSavedTime: "2026-03-25T10:00:00Z"
          },
          {
            documentId: "doc-2",
            title: "产品设计.docx",
            lastSavedTime: "2026-03-25T09:00:00Z"
          }
        ]
      }));
    window.confirm.mockReturnValue(true);

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.text()).toContain("返回文档列表");

    const returnButton = wrapper.findAll("button").find(button => button.text().includes("返回文档列表"));
    await returnButton.trigger("click");
    expect(routerPush).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-1" } });

    routerPush.mockClear();
    const switchButton = wrapper.findAll(".switch-item").find(button => button.text().includes("产品设计.docx"));
    await switchButton.trigger("click");

    expect(window.confirm).toHaveBeenCalled();
    expect(routerPush).toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-2" } });
  });

  it("应在用户取消确认时保持当前文档不切换", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse({
        documentId: "doc-1",
        title: "路线图.docx",
        status: "saved",
        lastSavedTime: "2026-03-25T10:00:00Z"
      }))
      .mockResolvedValueOnce(jsonResponse({
        documents: [
          {
            documentId: "doc-1",
            title: "路线图.docx",
            lastSavedTime: "2026-03-25T10:00:00Z"
          },
          {
            documentId: "doc-2",
            title: "产品设计.docx",
            lastSavedTime: "2026-03-25T09:00:00Z"
          }
        ]
      }));
    window.confirm.mockReturnValue(false);

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const switchButton = wrapper.findAll(".switch-item").find(button => button.text().includes("产品设计.docx"));
    await switchButton.trigger("click");

    expect(window.confirm).toHaveBeenCalled();
    expect(routerPush).not.toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-2" } });
  });
});
