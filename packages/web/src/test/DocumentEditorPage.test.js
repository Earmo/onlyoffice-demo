import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

const {
  routeState,
  routerPush,
  closeEditingSessionSpy,
  confirmMock
} = vi.hoisted(() => ({
  routeState: { params: { documentId: "doc-1" }, query: {} },
  routerPush: vi.fn(),
  closeEditingSessionSpy: vi.fn(),
  confirmMock: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush
  })
}));

vi.mock("element-plus", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    ElMessageBox: {
      confirm: confirmMock
    }
  };
});

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
    confirmMock.mockReset();
    confirmMock.mockResolvedValue("confirm");
  });

  it("应在返回列表前先弹出确认并结束当前编辑会话", async () => {
    mockEditorPageRequests();

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const returnButton = wrapper.findAll("button").find(button => button.text().includes("返回文档列表"));
    await returnButton.trigger("click");
    await flushPromises();

    expect(confirmMock).toHaveBeenCalledWith(
      "是否保存编辑并返回文档列表？",
      "返回确认",
      expect.objectContaining({ confirmButtonText: "保存并返回", cancelButtonText: "取消" })
    );
    expect(closeEditingSessionSpy).toHaveBeenCalledTimes(1);
    expect(routerPush).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-1" } });
    expect(closeEditingSessionSpy.mock.invocationCallOrder[0]).toBeLessThan(routerPush.mock.invocationCallOrder[0]);
  });

  it("应在切换文档前确认并先结束当前文档会话", async () => {
    mockEditorPageRequests();

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const switchButton = wrapper.findAll(".switch-item").find(card => card.text().includes("产品设计.docx"));
    await switchButton.trigger("click");
    await flushPromises();

    expect(confirmMock).toHaveBeenCalledWith(
      expect.stringContaining("产品设计.docx"),
      "切换文档",
      expect.objectContaining({ confirmButtonText: "确认切换", cancelButtonText: "取消" })
    );
    expect(closeEditingSessionSpy).toHaveBeenCalledTimes(1);
    expect(routerPush).toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-2" } });
    expect(closeEditingSessionSpy.mock.invocationCallOrder[0]).toBeLessThan(routerPush.mock.invocationCallOrder[0]);
  });

  it("应在重复触发离开时只发送一次 close-session", async () => {
    let resolveClose;
    closeEditingSessionSpy.mockReturnValue(new Promise(resolve => {
      resolveClose = resolve;
    }));
    mockEditorPageRequests();

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const returnButton = wrapper.findAll("button").find(button => button.text().includes("返回文档列表"));
    await returnButton.trigger("click");
    await flushPromises();
    await returnButton.trigger("click");
    await flushPromises();

    expect(closeEditingSessionSpy).toHaveBeenCalledTimes(1);
    resolveClose();
    await flushPromises();

    expect(routerPush).toHaveBeenCalledTimes(1);
    expect(routerPush).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-1" } });
  });

  it("应在结束编辑会话失败时停留当前页并展示错误", async () => {
    closeEditingSessionSpy.mockRejectedValueOnce(new Error("close failed"));
    mockEditorPageRequests();

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const returnButton = wrapper.findAll("button").find(button => button.text().includes("返回文档列表"));
    await returnButton.trigger("click");
    await flushPromises();

    expect(routerPush).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("close failed");
  });

  it("应在取消返回确认时留在编辑页", async () => {
    confirmMock.mockRejectedValueOnce("cancel");
    mockEditorPageRequests();

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    const returnButton = wrapper.findAll("button").find(button => button.text().includes("返回文档列表"));
    await returnButton.trigger("click");
    await flushPromises();

    expect(closeEditingSessionSpy).not.toHaveBeenCalled();
    expect(routerPush).not.toHaveBeenCalled();
  });
});

function mockEditorPageRequests() {
  fetch
    .mockResolvedValueOnce(jsonResponse({
      documentId: "doc-1",
      title: "路线图.docx",
      status: "saved",
      lastSavedTime: "2026-03-25T10:00:00Z"
    }))
    .mockResolvedValueOnce(jsonResponse({
      pageNumber: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
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
}
