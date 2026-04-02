import { mount } from "@vue/test-utils";
import { defineComponent, h, inject, provide } from "vue";
import { describe, expect, it } from "vitest";
import DocumentList from "../components/library/DocumentList.vue";

const TABLE_DATA_KEY = Symbol("table-data");

const ElCardStub = defineComponent({
  name: "ElCard",
  setup(_, { slots }) {
    return () => h("section", { class: "el-card-stub" }, [
      slots.header?.(),
      slots.default?.()
    ]);
  }
});

const ElButtonStub = defineComponent({
  name: "ElButton",
  inheritAttrs: false,
  emits: ["click"],
  setup(_, { attrs, emit, slots }) {
    return () => h("button", {
      ...attrs,
      onClick: event => {
        if (typeof attrs.onClick === "function") {
          attrs.onClick(event);
        } else if (Array.isArray(attrs.onClick)) {
          attrs.onClick.forEach(listener => listener(event));
        }
        emit("click", event);
      }
    }, slots.default?.());
  }
});

const ElTagStub = defineComponent({
  name: "ElTag",
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h("span", attrs, slots.default?.());
  }
});

const ElTableStub = defineComponent({
  name: "ElTable",
  props: {
    data: {
      type: Array,
      default: () => []
    }
  },
  setup(props, { slots }) {
    provide(TABLE_DATA_KEY, props.data);
    return () => h("div", { class: "el-table-stub" }, slots.default?.());
  }
});

const ElTableColumnStub = defineComponent({
  name: "ElTableColumn",
  props: {
    label: {
      type: String,
      default: ""
    },
    prop: {
      type: String,
      default: ""
    }
  },
  setup(props, { slots }) {
    const rows = inject(TABLE_DATA_KEY, []);
    return () => h("div", { class: "el-table-column-stub" }, [
      props.label ? h("div", { class: "column-label" }, props.label) : null,
      ...rows.map((row, index) => h("div", { class: "column-row", key: `${props.label}-${index}` }, [
        slots.default ? slots.default({ row }) : row?.[props.prop]
      ]))
    ]);
  }
});

describe("DocumentList", () => {
  it("应把开始编辑按钮放在提示语上方，并让两段提示语保持同级样式", () => {
    const wrapper = mountDocumentList();

    expect(wrapper.find(".start-edit-btn").exists()).toBe(true);
    expect(wrapper.find(".intro-copy").text()).toBe("先查看，再决定是否进入编辑");
    expect(wrapper.find(".helper-copy").text()).toContain("点击行可直接预览");
    expect(wrapper.find(".helper-copy").text()).toContain("“编辑”会进入独立可编辑工作台");
    expect(wrapper.find(".intro-copy").classes()).toContain("muted-copy");
    expect(wrapper.find(".helper-copy").classes()).toContain("muted-copy");
  });

  it("应展示最近编辑时间并支持预览、编辑、删除事件", async () => {
    const wrapper = mountDocumentList();

    expect(wrapper.find(".preview-document-btn").exists()).toBe(true);
    expect(wrapper.find(".edit-document-btn").exists()).toBe(true);
    expect(wrapper.find(".delete-document-btn").exists()).toBe(true);
    expect(wrapper.find(".preview-document-btn").text()).toBe("查看");
    expect(wrapper.find(".edit-document-btn").text()).toBe("编辑");
    expect(wrapper.find(".delete-document-btn").text()).toContain("删除");
    expect(wrapper.text()).toContain("最近编辑");
    expect(wrapper.text()).toContain("项目路线图.docx");

    await wrapper.find(".preview-document-btn").trigger("click");
    expect(wrapper.emitted("preview")?.[0]?.[0]?.documentId).toBe("doc-1");

    await wrapper.find(".edit-document-btn").trigger("click");
    expect(wrapper.emitted("edit")?.[0]?.[0]?.documentId).toBe("doc-1");

    await wrapper.find(".delete-document-btn").trigger("click");
    expect(wrapper.emitted("delete")?.[0]?.[0]?.documentId).toBe("doc-1");
  });
});

function mountDocumentList(overrides = {}) {
  return mount(DocumentList, {
    props: {
      documents: [documentSummary()],
      deletingDocumentId: "",
      ...overrides
    },
    global: {
      stubs: {
        ElCard: ElCardStub,
        ElButton: ElButtonStub,
        ElTag: ElTagStub,
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub
      }
    }
  });
}

function documentSummary(overrides = {}) {
  return {
    documentId: "doc-1",
    title: "项目路线图.docx",
    status: "saved",
    ownerUser: "owner-a",
    actorUser: "user-a",
    actorName: "Alice",
    sourceSystem: "native",
    documentType: "word",
    storageAvailable: true,
    lastEditedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}
