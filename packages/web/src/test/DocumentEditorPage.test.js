import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

const {
  routeState,
  routerPush,
  closeEditingSessionSpy
} = vi.hoisted(() => ({
  routeState: { params: { documentId: "doc-1" }, query: {} },
  routerPush: vi.fn(),
  closeEditingSessionSpy: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush
  })
}));

vi.mock("../components/editor/EditorShell.vue", () => ({
  default: defineComponent({
    name: "EditorShellStub",
    props: ["documentId", "documentTitle", "readonly", "showConsole"],
    setup(props, { expose }) {
      expose({
        closeEditingSession: closeEditingSessionSpy
      });
      return () => h("div", { class: "editor-shell-stub" }, `${props.documentTitle}::${props.documentId}`);
    }
  })
}));

import DocumentEditorPage from "../pages/DocumentEditorPage.vue";

describe("DocumentEditorPage", () => {
  beforeEach(() => {
    routeState.params = { documentId: "doc-1" };
    routeState.query = {};
    routerPush.mockReset();
    routerPush.mockResolvedValue(undefined);
    closeEditingSessionSpy.mockReset();
    closeEditingSessionSpy.mockResolvedValue(undefined);
    window.confirm = vi.fn();
  });

  it("应加载编辑页、支持收起顶部区域并在返回列表前结束会话", async () => {
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

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    expect(wrapper.text()).toContain("路线图.docx");
    expect(wrapper.text()).toContain("编辑提示");
    expect(wrapper.text()).toContain("返回文档列表");

    const collapseButton = wrapper.findAll("button").find(button => button.text().includes("收起提示"));
    await collapseButton.trigger("click");
    expect(wrapper.text()).not.toContain("编辑提示");
    expect(wrapper.text()).not.toContain("返回文档列表");
    expect(wrapper.text()).toContain("展开顶部信息");

    const revealButton = wrapper.findAll("button").find(button => button.text().includes("展开顶部信息"));
    await revealButton.trigger("click");
    expect(wrapper.text()).toContain("返回文档列表");

    const returnButton = wrapper.findAll("button").find(button => button.text().includes("返回文档列表"));
    await returnButton.trigger("click");
    await flushPromises();

    expect(closeEditingSessionSpy).toHaveBeenCalledTimes(1);
    expect(routerPush).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-1" } });
  });

  it("应在切换文档前确认并先结束当前文档会话", async () => {
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

    const switchButton = wrapper.findAll(".switch-item").find(button => button.text().includes("产品设计.docx"));
    await switchButton.trigger("click");
    await flushPromises();

    expect(window.confirm).toHaveBeenCalledWith("即将结束当前文档会话并打开“产品设计.docx”，是否继续？");
    expect(closeEditingSessionSpy).toHaveBeenCalledTimes(1);
    expect(routerPush).toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-2" } });
  });

  it("应把当前文档侧栏固定在编辑器左侧", async () => {
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
          }
        ]
      }));

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const layout = wrapper.find(".editor-layout");
    expect(layout.element.firstElementChild?.className).toContain("editor-sidebar");
    expect(layout.element.lastElementChild?.className).toContain("editor-stage");
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
    await flushPromises();

    expect(window.confirm).toHaveBeenCalled();
    expect(closeEditingSessionSpy).not.toHaveBeenCalled();
    expect(routerPush).not.toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-2" } });
  });
});
