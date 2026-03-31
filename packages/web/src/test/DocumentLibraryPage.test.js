import { mount } from "@vue/test-utils";
import { defineComponent } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, jsonResponse } from "./helpers";

const { routeState, routerPush, routerReplace } = vi.hoisted(() => ({
  routeState: { query: {} },
  routerPush: vi.fn(),
  routerReplace: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush,
    replace: routerReplace
  })
}));

vi.mock("../components/library/DocumentCreateActions.vue", () => ({
  default: defineComponent({
    name: "DocumentCreateActionsStub",
    emits: ["create", "file-selected", "import-remote", "update:remoteDocumentUrl"],
    template: `
      <div class="document-create-actions-stub">
        <button class="create-doc" @click="$emit('create')">新建空白文档</button>
      </div>
    `
  })
}));

vi.mock("../components/library/DocumentList.vue", () => ({
  default: defineComponent({
    name: "DocumentListStub",
    props: {
      documents: {
        type: Array,
        default: () => []
      },
      highlightedDocumentId: {
        type: String,
        default: ""
      }
    },
    emits: ["preview", "edit", "start-edit"],
    template: `
      <div class="document-list-stub">
        <button class="start-edit-btn" @click="$emit('start-edit')">开始编辑</button>
        <div class="highlighted-document">{{ highlightedDocumentId }}</div>
        <div v-for="document in documents" :key="document.documentId" class="document-row">
          {{ document.title }}|{{ document.status }}
        </div>
        <button v-if="documents[0]" class="preview-doc" @click="$emit('preview', documents[0])">查看文件</button>
        <button v-if="documents[0]" class="edit-doc" @click="$emit('edit', documents[0])">编辑文档</button>
      </div>
    `
  })
}));

import DocumentLibraryPage from "../pages/DocumentLibraryPage.vue";

describe("DocumentLibraryPage", () => {
  beforeEach(() => {
    routeState.query = {};
    routerPush.mockReset();
    routerPush.mockResolvedValue(undefined);
    routerReplace.mockReset();
    routerReplace.mockResolvedValue(undefined);
    window.prompt = vi.fn();
  });

  it("应按后端分页参数加载文档工作台，并展示当前上下文与高亮文档", async () => {
    routeState.query = { highlight: "doc-1" };
    fetch.mockResolvedValueOnce(jsonResponse(listPayload({
      total: 42,
      totalPages: 5,
      documents: [documentSummary({ documentId: "doc-1", title: "项目路线图.docx" })]
    })));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(requestUrl(0).pathname).toBe("/api/documents");
    expect(requestUrl(0).searchParams.get("pageNumber")).toBe("1");
    expect(requestUrl(0).searchParams.get("pageSize")).toBe("10");
    expect(fetch.mock.calls[0][1]?.headers?.["X-External-User-Id"]).toBe("starter-user");
    expect(wrapper.text()).toContain("tenant-a");
    expect(wrapper.text()).toContain("Alice");
    expect(wrapper.text()).toContain("项目路线图.docx");
    expect(wrapper.find(".highlighted-document").text()).toBe("doc-1");
    expect(wrapper.findComponent({ name: "ElPagination" }).props("total")).toBe(42);
  });

  it("应分别把查看文件和编辑文档路由到预览页与编辑页", async () => {
    fetch.mockResolvedValueOnce(jsonResponse(listPayload({
      documents: [documentSummary({ documentId: "doc-1", title: "项目路线图.docx" })]
    })));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    await wrapper.find(".preview-doc").trigger("click");
    expect(routerPush).toHaveBeenCalledWith({ name: "preview", params: { documentId: "doc-1" } });

    routerPush.mockClear();
    await wrapper.find(".edit-doc").trigger("click");
    expect(routerPush).toHaveBeenCalledWith({ name: "editor", params: { documentId: "doc-1" } });
  });

  it("应在新建文档后回流第一页并显示成功提示", async () => {
    window.prompt.mockReturnValue("新计划.docx");
    fetch
      .mockResolvedValueOnce(jsonResponse(listPayload({ documents: [] })))
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
      .mockResolvedValueOnce(jsonResponse(listPayload({
        documents: [documentSummary({ documentId: "doc-2", title: "新计划.docx", status: "draft" })]
      })));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    if (wrapper.find(".start-edit-empty-btn").exists()) {
      await wrapper.find(".start-edit-empty-btn").trigger("click");
    } else {
      await wrapper.find(".start-edit-btn").trigger("click");
    }
    await flushPromises();

    // Since ElDialog might transport content using Teleport to document.body,
    // we should emit 'create' directly from the DocumentCreateActionsStub component instance
    // or simulate the event on the stub if it can be found.
    const createActions = wrapper.findComponent({ name: "DocumentCreateActionsStub" });
    if (createActions.exists()) {
      createActions.vm.$emit("create");
    } else {
      // Fallback for document.body portaled nodes in some test setups
      document.querySelector(".create-doc").click();
    }
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(3);
    expect(requestUrl(1).pathname).toBe("/api/documents");
    expect(fetch.mock.calls[1][1]?.method).toBe("POST");
    expect(fetch.mock.calls[1][1]?.body).toContain("\"title\":\"新计划.docx\"");
    expect(routerReplace).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-2" } });
    expect(requestUrl(2).searchParams.get("pageNumber")).toBe("1");
    expect(requestUrl(2).searchParams.get("pageSize")).toBe("10");
    expect(wrapper.text()).toContain("已创建 新计划.docx，结果已回到工作台列表。");
    expect(wrapper.text()).toContain("新计划.docx|draft");
  });

  it("应在翻页和修改每页条数时重新请求后端，并使用响应中的总数", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(listPayload({
        total: 42,
        totalPages: 5,
        documents: [documentSummary({ documentId: "doc-1", title: "第一页文档.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(listPayload({
        pageNumber: 2,
        pageSize: 10,
        total: 42,
        totalPages: 5,
        documents: [documentSummary({ documentId: "doc-2", title: "第二页文档.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(listPayload({
        pageNumber: 1,
        pageSize: 50,
        total: 42,
        totalPages: 1,
        documents: [documentSummary({ documentId: "doc-3", title: "大页文档.docx" })]
      })));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    const pagination = wrapper.findComponent({ name: "ElPagination" });
    pagination.vm.$emit("update:current-page", 2);
    pagination.vm.$emit("current-change", 2);
    await flushPromises();

    expect(requestUrl(1).searchParams.get("pageNumber")).toBe("2");
    expect(requestUrl(1).searchParams.get("pageSize")).toBe("10");
    expect(wrapper.text()).toContain("第二页文档.docx|saved");

    pagination.vm.$emit("update:page-size", 50);
    pagination.vm.$emit("size-change", 50);
    await flushPromises();

    expect(requestUrl(2).searchParams.get("pageNumber")).toBe("1");
    expect(requestUrl(2).searchParams.get("pageSize")).toBe("50");
    expect(wrapper.findComponent({ name: "ElPagination" }).props("total")).toBe(42);
    expect(wrapper.text()).toContain("大页文档.docx|saved");
  });
});

function requestUrl(callIndex) {
  return new URL(fetch.mock.calls[callIndex][0], "http://example.test");
}

function listPayload({
  pageNumber = 1,
  pageSize = 10,
  total = 1,
  totalPages = 1,
  documents = [documentSummary()]
} = {}) {
  return {
    tenantId: "tenant-a",
    actorUser: "user-a",
    actorName: "Alice",
    pageNumber,
    pageSize,
    total,
    totalPages,
    documents
  };
}

function documentSummary(overrides = {}) {
  return {
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
    lastSavedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}
