import { mount } from "@vue/test-utils";
import { defineComponent, h, inject, provide } from "vue";
import { describe, expect, it } from "vitest";
import DocumentList from "../components/library/DocumentList.vue";

// 这些 stub 只复刻本用例真正依赖的 Element Plus 行为，
// 让列表测试可以保持轻量、稳定，不受真实表格实现细节影响。
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
  props: {
    loading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false }
  },
  emits: ["click"],
  setup(props, { attrs, emit, slots }) {
    return () => h("button", {
      ...attrs,
      "data-loading": props.loading ? "true" : undefined,
      "data-disabled": props.disabled ? "true" : undefined,
      disabled: props.disabled || undefined,
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

// ElDropdown stub：渲染默认 slot（触发按钮）和 dropdown slot（菜单内容），
// 并把 command 作为自定义事件暴露，让菜单项点击可以被测试感知。
const ElDropdownStub = defineComponent({
  name: "ElDropdown",
  props: {
    disabled: { type: Boolean, default: false }
  },
  emits: ["command"],
  setup(props, { slots, emit }) {
    const handleCommand = (cmd) => {
      if (!props.disabled) {
        emit("command", cmd);
      }
    };
    // 通过 provide 把 handleCommand 给 ElDropdownItem 使用
    provide("dropdown-command-handler", handleCommand);
    return () => h("div", {
      class: "el-dropdown-stub",
      "data-disabled": props.disabled ? "true" : undefined
    }, [
      // 默认 slot（触发按钮）
      h("span", { class: "el-dropdown-trigger" }, slots.default?.()),
      // dropdown slot（菜单）
      h("div", { class: "el-dropdown-menu-stub" }, slots.dropdown?.())
    ]);
  }
});

const ElDropdownMenuStub = defineComponent({
  name: "ElDropdownMenu",
  setup(_, { slots }) {
    return () => h("ul", { class: "el-dropdown-menu" }, slots.default?.());
  }
});

const ElDropdownItemStub = defineComponent({
  name: "ElDropdownItem",
  inheritAttrs: false,
  props: {
    command: { type: String, default: "" },
    divided: { type: Boolean, default: false }
  },
  setup(props, { slots, attrs }) {
    const handleCommand = inject("dropdown-command-handler", null);
    return () => h("li", {
      ...attrs,
      class: ["el-dropdown-item", props.divided ? "is-divided" : ""],
      onClick: () => {
        if (handleCommand) handleCommand(props.command);
      }
    }, slots.default?.());
  }
});

const ElIconStub = defineComponent({
  name: "ElIcon",
  setup(_, { slots }) {
    return () => h("i", { class: "el-icon-stub" }, slots.default?.());
  }
});

const ArrowDownStub = defineComponent({
  name: "ArrowDown",
  setup() {
    return () => h("i", { class: "arrow-down-icon" });
  }
});

describe("DocumentList", () => {
  it("应把开始编辑按钮放在提示语上方，并让两段提示语保持同级样式", () => {
    const wrapper = mountDocumentList();

    expect(wrapper.find(".start-edit-btn").exists()).toBe(true);
    expect(wrapper.find(".intro-copy").text()).toBe("先查看，再决定是否进入编辑");
    expect(wrapper.find(".helper-copy").text()).toContain("点击行可直接预览");
    expect(wrapper.find(".helper-copy").text()).toContain("\u201c编辑\u201d会进入独立可编辑工作台");
    expect(wrapper.find(".intro-copy").classes()).toContain("muted-copy");
    expect(wrapper.find(".helper-copy").classes()).toContain("muted-copy");
  });

  it("应展示最近编辑时间并支持预览、编辑事件", async () => {
    const wrapper = mountDocumentList();

    expect(wrapper.find(".preview-document-btn").exists()).toBe(true);
    expect(wrapper.find(".edit-document-btn").exists()).toBe(true);
    expect(wrapper.find(".preview-document-btn").text()).toBe("查看");
    expect(wrapper.find(".edit-document-btn").text()).toBe("编辑");
    expect(wrapper.text()).toContain("最近编辑");
    expect(wrapper.text()).toContain("项目路线图.docx");

    await wrapper.find(".preview-document-btn").trigger("click");
    expect(wrapper.emitted("preview")?.[0]?.[0]?.documentId).toBe("doc-1");

    await wrapper.find(".edit-document-btn").trigger("click");
    expect(wrapper.emitted("edit")?.[0]?.[0]?.documentId).toBe("doc-1");
  });

  it("PDF 文档行的「更多」菜单中应包含「转换为 Word」菜单项", () => {
    const wrapper = mountDocumentList({
      documents: [pdfDocumentSummary()]
    });
    // 菜单项渲染在 dropdown slot 内，文本应包含「转换为 Word」
    const dropdownMenu = wrapper.find(".el-dropdown-menu-stub");
    expect(dropdownMenu.text()).toContain("转换为 Word");
    expect(dropdownMenu.text()).not.toContain("消除水印");
  });

  it("Word（docx）文档行的「更多」菜单中应包含「消除水印」菜单项", () => {
    const wrapper = mountDocumentList({
      documents: [docxDocumentSummary()]
    });
    const dropdownMenu = wrapper.find(".el-dropdown-menu-stub");
    expect(dropdownMenu.text()).toContain("消除水印");
    expect(dropdownMenu.text()).not.toContain("转换为 Word");
  });

  it("其他类型文档行的「更多」菜单中只有「删除」，无转换/水印选项", () => {
    const wrapper = mountDocumentList({
      documents: [documentSummary({ fileType: "pptx", documentType: "slide" })]
    });
    const dropdownMenu = wrapper.find(".el-dropdown-menu-stub");
    expect(dropdownMenu.text()).toContain("删除");
    expect(dropdownMenu.text()).not.toContain("转换为 Word");
    expect(dropdownMenu.text()).not.toContain("消除水印");
  });

  it("点击「转换为 Word」菜单项时，应 emit「convert」事件并携带正确的 doc 对象", async () => {
    const doc = pdfDocumentSummary();
    const wrapper = mountDocumentList({ documents: [doc] });

    // 找到「转换为 Word」的 dropdown-item 并点击
    const convertItem = wrapper.findAll(".el-dropdown-item").find(el => el.text().includes("转换为 Word"));
    expect(convertItem).toBeTruthy();
    await convertItem.trigger("click");

    expect(wrapper.emitted("convert")).toBeTruthy();
    expect(wrapper.emitted("convert")?.[0]?.[0]?.documentId).toBe("pdf-1");
  });

  it("点击「消除水印」菜单项时，应 emit「remove-watermark」事件并携带正确的 doc 对象", async () => {
    const doc = docxDocumentSummary();
    const wrapper = mountDocumentList({ documents: [doc] });

    const watermarkItem = wrapper.findAll(".el-dropdown-item").find(el => el.text().includes("消除水印"));
    expect(watermarkItem).toBeTruthy();
    await watermarkItem.trigger("click");

    expect(wrapper.emitted("remove-watermark")).toBeTruthy();
    expect(wrapper.emitted("remove-watermark")?.[0]?.[0]?.documentId).toBe("docx-1");
  });

  it("点击「删除」菜单项时，应 emit「delete」事件并携带正确的 doc 对象", async () => {
    const doc = documentSummary();
    const wrapper = mountDocumentList({ documents: [doc] });

    const deleteItem = wrapper.findAll(".el-dropdown-item").find(el => el.text().includes("删除"));
    expect(deleteItem).toBeTruthy();
    await deleteItem.trigger("click");

    expect(wrapper.emitted("delete")).toBeTruthy();
    expect(wrapper.emitted("delete")?.[0]?.[0]?.documentId).toBe("doc-1");
  });

  it("processingDocumentId 匹配时，对应行的「更多▼」按钮应处于 loading/disabled 状态", () => {
    const doc = documentSummary({ documentId: "doc-processing" });
    const wrapper = mountDocumentList({
      documents: [doc],
      processingDocumentId: "doc-processing"
    });

    const moreBtn = wrapper.find(".more-document-btn");
    expect(moreBtn.exists()).toBe(true);
    // ElButtonStub 会把 loading prop 映射为 data-loading 属性
    expect(moreBtn.attributes("data-loading")).toBe("true");
  });

  it("processingDocumentId 不匹配时，「更多▼」按钮不应处于 loading 状态", () => {
    const doc = documentSummary({ documentId: "doc-normal" });
    const wrapper = mountDocumentList({
      documents: [doc],
      processingDocumentId: "other-doc-id"
    });

    const moreBtn = wrapper.find(".more-document-btn");
    expect(moreBtn.attributes("data-loading")).toBeUndefined();
  });
});

function mountDocumentList(overrides = {}) {
  // 统一的 mount 工厂，方便后续继续扩展高亮、删除 loading 等场景。
  const { documents = [documentSummary()], ...propsOverrides } = overrides;
  return mount(DocumentList, {
    props: {
      documents,
      deletingDocumentId: "",
      processingDocumentId: "",
      ...propsOverrides
    },
    global: {
      stubs: {
        ElCard: ElCardStub,
        ElButton: ElButtonStub,
        ElTag: ElTagStub,
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub,
        ElDropdown: ElDropdownStub,
        ElDropdownMenu: ElDropdownMenuStub,
        ElDropdownItem: ElDropdownItemStub,
        ElIcon: ElIconStub,
        ArrowDown: ArrowDownStub
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
    fileType: "docx",
    storageAvailable: true,
    lastEditedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}

function pdfDocumentSummary(overrides = {}) {
  return {
    documentId: "pdf-1",
    title: "报告.pdf",
    status: "saved",
    ownerUser: "owner-a",
    actorUser: "user-a",
    actorName: "Alice",
    sourceSystem: "native",
    documentType: "pdf",
    fileType: "pdf",
    storageAvailable: true,
    lastEditedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}

function docxDocumentSummary(overrides = {}) {
  return {
    documentId: "docx-1",
    title: "合同.docx",
    status: "saved",
    ownerUser: "owner-a",
    actorUser: "user-a",
    actorName: "Alice",
    sourceSystem: "native",
    documentType: "word",
    fileType: "docx",
    storageAvailable: true,
    lastEditedTime: "2026-03-25T10:00:00Z",
    ...overrides
  };
}
