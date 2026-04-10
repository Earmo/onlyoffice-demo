import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

const { routeState, routerPush } = vi.hoisted(() => ({
  routeState: { params: { documentId: "doc-1" }, query: {} },
  routerPush: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush
  })
}));

vi.mock("../components/editor/EditorShell.vue", () => ({
  default: defineComponent({
    name: "EditorShellPreviewStub",
    props: ["documentId", "documentTitle", "readonly", "showConsole"],
    setup(props) {
      return () => h(
        "div",
        {
          class: "preview-editor-shell-stub",
          "data-readonly": String(props.readonly),
          "data-show-console": String(props.showConsole)
        },
        `${props.documentTitle}::${props.documentId}`
      );
    }
  })
}));

import DocumentPreviewPage from "../pages/DocumentPreviewPage.vue";

describe("DocumentPreviewPage", () => {
  beforeEach(() => {
    routeState.params = { documentId: "doc-1" };
    routerPush.mockReset();
    routerPush.mockResolvedValue(undefined);
  });

  it("应以只读方式加载预览页并支持进入编辑", async () => {
    // 预览页的关键约束是：只读打开 EditorShell，同时不展示右侧控制台。
    fetch.mockResolvedValueOnce(jsonResponse({
      documentId: "doc-1",
      title: "预览稿.docx",
      status: "saved",
      lastSavedTime: "2026-03-25T10:00:00Z"
    }));

    const wrapper = mount(DocumentPreviewPage);
    await flushPromises();

    expect(wrapper.text()).toContain("只读预览");
    expect(wrapper.find(".preview-editor-shell-stub").attributes("data-readonly")).toBe("true");
    expect(wrapper.find(".preview-editor-shell-stub").attributes("data-show-console")).toBe("false");

    const editButton = wrapper.findAll("button").find(button => button.text().includes("编辑文档"));
    await editButton.trigger("click");
    expect(routerPush).toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-1" } });
  });
});
