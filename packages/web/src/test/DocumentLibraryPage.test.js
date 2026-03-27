import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

let routeState = { query: {} };
const routerPush = vi.fn();
const routerReplace = vi.fn();

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush,
    replace: routerReplace
  })
}));

import DocumentLibraryPage from "../pages/DocumentLibraryPage.vue";

describe("DocumentLibraryPage", () => {
  beforeEach(() => {
    routeState = { query: {} };
    routerPush.mockReset();
    routerPush.mockResolvedValue(undefined);
    routerReplace.mockReset();
    routerReplace.mockResolvedValue(undefined);
    window.prompt = vi.fn();
  });

  it("应加载文档工作台并展示当前上下文与高亮文档", async () => {
    routeState.query = { highlight: "doc-1" };
    fetch.mockResolvedValueOnce(jsonResponse({
      tenantId: "tenant-a",
      actorUser: "user-a",
      actorName: "Alice",
      documents: [
        {
          documentId: "doc-1",
          title: "项目路线图.docx",
          status: "saved",
          tenantId: "tenant-a",
          ownerUser: "owner-a",
          actorUser: "user-a",
          actorName: "Alice",
          sourceSystem: "native",
          documentType: "word",
          storageAvailable: true,
          lastSavedTime: "2026-03-25T10:00:00Z"
        }
      ]
    }));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][0]).toContain("/api/documents");
    expect(fetch.mock.calls[0][1]?.headers?.["X-External-User-Id"]).toBe("starter-user");
    expect(wrapper.text()).toContain("tenant-a");
    expect(wrapper.text()).toContain("Alice");
    expect(wrapper.text()).toContain("项目路线图.docx");
    expect(wrapper.find(".document-row.highlighted").exists()).toBe(true);
  });

  it("应在新建文档后回流列表并显示成功提示", async () => {
    window.prompt.mockReturnValue("新计划.docx");
    fetch
      .mockResolvedValueOnce(jsonResponse({
        tenantId: "tenant-a",
        actorUser: "user-a",
        actorName: "Alice",
        documents: []
      }))
      .mockResolvedValueOnce(jsonResponse({
        documentId: "doc-2",
        title: "新计划.docx",
        ownerUser: "user-a",
        actorUser: "user-a",
        actorName: "Alice",
        sourceSystem: "native",
        documentType: "word",
        storageAvailable: true,
        status: "draft"
      }))
      .mockResolvedValueOnce(jsonResponse({
        tenantId: "tenant-a",
        actorUser: "user-a",
        actorName: "Alice",
        documents: [
          {
            documentId: "doc-2",
            title: "新计划.docx",
            status: "draft",
            tenantId: "tenant-a",
            ownerUser: "user-a",
            actorUser: "user-a",
            actorName: "Alice",
            sourceSystem: "native",
            documentType: "word",
            storageAvailable: true,
            lastSavedTime: null
          }
        ]
      }));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    const createButton = wrapper.findAll("button").find(button => button.text().includes("新建空白文档"));
    await createButton.trigger("click");
    await flushPromises();

    expect(routerReplace).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-2" } });
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(wrapper.text()).toContain("已创建 新计划.docx，结果已回到工作台列表。");
    expect(wrapper.text()).toContain("新计划.docx");
  });
});
