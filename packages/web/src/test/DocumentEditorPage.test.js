import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

const {
  routeState,
  routerPush,
  closeEditingSessionSpy,
  confirmMock,
  routeLeaveGuard
} = vi.hoisted(() => ({
  routeState: { params: { documentId: "doc-1" }, query: {} },
  routerPush: vi.fn(),
  closeEditingSessionSpy: vi.fn(),
  confirmMock: vi.fn(),
  routeLeaveGuard: { current: null }
}));

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush
  }),
  onBeforeRouteLeave: (guard) => {
    routeLeaveGuard.current = guard;
  }
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
    routeLeaveGuard.current = null;
    routerPush.mockReset();
    routerPush.mockResolvedValue(undefined);
    closeEditingSessionSpy.mockReset();
    closeEditingSessionSpy.mockResolvedValue(undefined);
    confirmMock.mockReset();
    confirmMock.mockResolvedValue("confirm");
  });

  it("应在路由离开前先结束当前文档会话", async () => {
    mockEditorPageRequests();

    const wrapper = mount(DocumentEditorPage);
    await flushPromises();

    expect(routeLeaveGuard.current).toEqual(expect.any(Function));

    const leaveResult = await routeLeaveGuard.current(
      { name: "library", path: "/" },
      { name: "editor", path: "/editor/doc-1" }
    );

    expect(closeEditingSessionSpy).toHaveBeenCalledTimes(1);
    expect(leaveResult).toBeUndefined();
    expect(routerPush).not.toHaveBeenCalled();
  });
});

function mockEditorPageRequests() {
  // 编辑页当前初始化只依赖当前文档详情。
  fetch
    .mockResolvedValueOnce(jsonResponse({
      documentId: "doc-1",
      title: "路线图.docx",
      status: "saved",
      lastSavedTime: "2026-03-25T10:00:00Z"
    }));
}
