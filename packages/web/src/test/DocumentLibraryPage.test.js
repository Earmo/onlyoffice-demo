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
    emits: ["preview", "edit", "delete", "start-edit"],
    template: `
      <div class="document-list-stub">
        <button class="start-edit-btn" @click="$emit('start-edit')">开始编辑</button>
        <div class="highlighted-document">{{ highlightedDocumentId }}</div>
        <div v-for="document in documents" :key="document.documentId" class="document-row">
          {{ document.title }}|{{ document.status }}
        </div>
        <button v-if="documents[0]" class="preview-doc" @click="$emit('preview', documents[0])">查看文件</button>
        <button v-if="documents[0]" class="edit-doc" @click="$emit('edit', documents[0])">编辑文档</button>
        <button v-if="documents[0]" class="delete-doc" @click="$emit('delete', documents[0])">删除文档</button>
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
    vi.stubGlobal("confirm", vi.fn(() => true));
  });

  it("应按后端分页参数加载文档工作台，并展示当前上下文与高亮文档", async () => {
    // 首页初始化时会并行拉“主列表 + 最近文档”，这个行为在分页改造后很容易被改坏。
    routeState.query = { highlight: "doc-1" };
    fetch
      .mockResolvedValueOnce(jsonResponse(listPayload({
        total: 42,
        totalPages: 5,
        documents: [documentSummary({ documentId: "doc-1", title: "项目路线图.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(recentPayload([
        recentSummary({ documentId: "doc-9", title: "最近编辑文档.docx" })
      ])));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(2);
    expect(requestUrl(0).pathname).toBe("/api/documents/page");
    expect(requestBody(0).pageNumber).toBe(1);
    expect(requestBody(0).pageSize).toBe(10);
    expect(requestBody(0).sortDirection).toBeUndefined();
    expect(fetch.mock.calls[0][1]?.headers?.["Content-Type"]).toBe("application/json");
    expect(requestUrl(1).pathname).toBe("/api/documents/list/recent");
    expect(requestBody(1).limit).toBe(3);
    expect(fetch.mock.calls[1][1]?.headers?.["Content-Type"]).toBe("application/json");
    expect(fetch.mock.calls[0][1]?.headers?.["X-External-User-Id"]).toBe("starter-user");
    expect(wrapper.text()).toContain("tenant-a");
    expect(wrapper.text()).toContain("Alice");
    expect(wrapper.text()).toContain("项目路线图.docx");
    expect(wrapper.text()).toContain("最近编辑文档.docx");
    const recentTooltip = wrapper.findAllComponents({ name: "ElTooltip" })
      .find(c => c.props("content") === "最近编辑文档.docx");
    expect(recentTooltip).toBeDefined();
    expect(wrapper.find(".highlighted-document").text()).toBe("doc-1");
    expect(wrapper.findComponent({ name: "ElPagination" }).props("total")).toBe(42);
  });

  it("应分别把查看文件和编辑文档路由到预览页与编辑页", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(listPayload({
        documents: [documentSummary({ documentId: "doc-1", title: "项目路线图.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(recentPayload()));

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
      .mockResolvedValueOnce(jsonResponse(listPayload({ documents: [], total: 0, totalPages: 0 })))
      .mockResolvedValueOnce(jsonResponse(recentPayload()))
      .mockResolvedValueOnce(jsonResponse({
        documentId: "doc-2",
        title: "新计划.docx",
        ownerUser: "user-a",
        actorUser: "user-a",
        actorName: "Alice",
        sourceSystem: "native",
        documentType: "word",
        storageAvailable: true,
        status: "draft",
        lastEditedTime: "2026-03-25T10:00:00Z"
      }))
      .mockResolvedValueOnce(jsonResponse(listPayload({
        documents: [documentSummary({ documentId: "doc-2", title: "新计划.docx", status: "draft" })]
      })))
      .mockResolvedValueOnce(jsonResponse(recentPayload([
        recentSummary({ documentId: "doc-2", title: "新计划.docx" })
      ])));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    if (wrapper.find(".start-edit-empty-btn").exists()) {
      await wrapper.find(".start-edit-empty-btn").trigger("click");
    } else {
      await wrapper.find(".start-edit-btn").trigger("click");
    }
    await flushPromises();

    // ElDialog 可能通过 Teleport 把内容挂到 document.body，
    // 所以这里优先直接从 stub 组件实例发 create 事件，必要时再退回 DOM 点击。
    const createActions = wrapper.findComponent({ name: "DocumentCreateActionsStub" });
    if (createActions.exists()) {
      createActions.vm.$emit("create");
    } else {
      // 某些测试环境下找不到 stub 实例时，兜底点一下 teleport 后的按钮。
      document.querySelector(".create-doc").click();
    }
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(5);
    expect(requestUrl(2).pathname).toBe("/api/documents/create");
    expect(fetch.mock.calls[2][1]?.method).toBe("POST");
    expect(fetch.mock.calls[2][1]?.body).toContain("\"title\":\"新计划.docx\"");
    expect(routerReplace).toHaveBeenCalledWith({ path: "/", query: { highlight: "doc-2" } });
    expect(requestBody(3).pageNumber).toBe(1);
    expect(requestBody(3).pageSize).toBe(10);
    expect(requestUrl(4).pathname).toBe("/api/documents/list/recent");
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
      .mockResolvedValueOnce(jsonResponse(recentPayload()))
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

    expect(requestBody(2).pageNumber).toBe(2);
    expect(requestBody(2).pageSize).toBe(10);
    expect(wrapper.text()).toContain("第二页文档.docx|saved");

    pagination.vm.$emit("update:page-size", 50);
    pagination.vm.$emit("size-change", 50);
    await flushPromises();

    expect(requestBody(3).pageNumber).toBe(1);
    expect(requestBody(3).pageSize).toBe(50);
    expect(wrapper.findComponent({ name: "ElPagination" }).props("total")).toBe(42);
    expect(wrapper.text()).toContain("大页文档.docx|saved");
  });

  it("应在删除文档后刷新列表与最近文档，并清理高亮参数", async () => {
    routeState.query = { highlight: "doc-1" };
    fetch
      .mockResolvedValueOnce(jsonResponse(listPayload({
        documents: [documentSummary({ documentId: "doc-1", title: "待删除文档.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(recentPayload([
        recentSummary({ documentId: "doc-1", title: "待删除文档.docx" })
      ])))
      .mockResolvedValueOnce(jsonResponse({}, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(listPayload({
        documents: [documentSummary({ documentId: "doc-2", title: "保留文档.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(recentPayload([
        recentSummary({ documentId: "doc-2", title: "保留文档.docx" })
      ])));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    await wrapper.find(".delete-doc").trigger("click");
    await flushPromises();

    expect(window.confirm).toHaveBeenCalledWith("确认删除《待删除文档.docx》吗？删除后它不会再出现在文档列表和最近文档中。");
    expect(fetch).toHaveBeenCalledTimes(5);
    expect(requestUrl(2).pathname).toBe("/api/documents/doc-1");
    expect(fetch.mock.calls[2][1]?.method).toBe("DELETE");
    expect(routerReplace).toHaveBeenCalledWith({ path: "/", query: {} });
    expect(requestUrl(3).pathname).toBe("/api/documents/page");
    expect(requestUrl(4).pathname).toBe("/api/documents/list/recent");
    expect(wrapper.text()).toContain("已删除 待删除文档.docx。");
    expect(wrapper.text()).toContain("保留文档.docx|saved");
  });

  it("应能打开上下文表单切换并保存自定义上下文进而重新加载和触发请求头更新", async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(listPayload()))
      .mockResolvedValueOnce(jsonResponse(recentPayload()))
      .mockResolvedValueOnce(jsonResponse(listPayload({
        tenantId: "my-tenant",
        actorUser: "custom-user",
        actorName: "John Doe",
        documents: [documentSummary({ documentId: "doc-new", title: "自定义身份查看到的文档.docx" })]
      })))
      .mockResolvedValueOnce(jsonResponse(recentPayload()));

    const wrapper = mount(DocumentLibraryPage);
    await flushPromises();

    wrapper.vm.openContextDialog();
    await flushPromises();

    wrapper.vm.contextForm.tenantId = "my-tenant";
    wrapper.vm.contextForm.actorUser = "custom-user";
    wrapper.vm.contextForm.actorName = "John Doe";
    wrapper.vm.contextForm.sourceSystem = "admin-sys";

    await wrapper.vm.saveContext();
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(4);
    expect(requestUrl(2).pathname).toBe("/api/documents/page");
    expect(fetch.mock.calls[2][1]?.headers?.["X-Tenant-Id"]).toBe("my-tenant");
    expect(fetch.mock.calls[2][1]?.headers?.["X-External-User-Id"]).toBe("custom-user");
    expect(fetch.mock.calls[2][1]?.headers?.["X-User-Display-Name"]).toBe("John Doe");
    expect(fetch.mock.calls[2][1]?.headers?.["X-Source-System"]).toBe("admin-sys");

    expect(wrapper.text()).toContain("自定义身份查看到的文档.docx");
    expect(wrapper.text()).toContain("已更新访问上下文。");
  });
});

function requestUrl(callIndex) {
  // 统一把 fetch 调用还原成 URL 对象，便于断言 query 参数。
  return new URL(fetch.mock.calls[callIndex][0], "http://example.test");
}

function requestBody(callIndex) {
  return JSON.parse(fetch.mock.calls[callIndex][1]?.body || "{}");
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

function recentPayload(documents = [recentSummary()]) {
  return documents;
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
    lastEditedTime: "2026-03-25T10:00:00Z",
    lastSavedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}

function recentSummary(overrides = {}) {
  return {
    documentId: "doc-9",
    title: "最近编辑文档.docx",
    lastEditedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}
