# Phase 10: element-plus-element-plus - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

引入并全面使用 `element-plus` 替换前端所有的自定义样式与组件（涉及工作台首页、文档列表、编辑工作台布局等）。不涉及增减核心功能，主要目标是前端工程化升级并减少自定义样式的维护成本。

</domain>

<decisions>
## Implementation Decisions

### Element Plus 引入策略
- **D-01:** 采用按需引入（搭配 `unplugin-vue-components` 和 `unplugin-auto-import`），确保打包体积最轻量化，符合前端微服务分发趋势。

### 主题与配色
- **D-02:** 接入成本最低的完全默认 Element Plus 基础蓝色主题，暂不引入 SCSS 覆盖自定义品牌色。

### 首页文档列表呈现
- **D-03:** 完全改用数据表格 (`el-table`) 展示文档工作台，提升“作者、最后修改时间、存储状态”等字段的信息展示密度。

### 图标资产
- **D-04:** 弃用原有/离散的字体图标，全面集成并切换至官方配套的 `@element-plus/icons-vue`。

### the agent's Discretion
- 具体如何用 `<el-container>` 等布局组件替换现有 `EditorWorkbench.vue` 和 `Home.vue` 的结构细节。
- 使用按需引入时的 Vite 配置和类型声明生成位置。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope
- `.planning/PROJECT.md` — 确保继续以前后端分离原则演进，确保编辑工作流闭环不被破坏。
- `.planning/ROADMAP.md` — Phase 10 属于整个工作台界面的 Element UI 重构。

### Frontend Setup
- `packages/web/package.json` — 现有的 Vite 配置基线。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Home.vue`、`EditorWorkbench.vue` 等核心布局需要迁移到 Element Plus 标准组件上，原有的纯 CSS flex 布局应尽量剥离。
- API 服务调用层（如 `fetchDocuments` 等）必须在重构期间保持不变。

### Established Patterns
- 目前通过 Vue Router 控制工作台和独立编辑器的切换。

### Integration Points
- 任何 UI 的替换（例如修改点击“打开文档”的处理逻辑）都不能破坏 Phase 3/Phase 4/Phase 9 已经确立的上下文本地存储和分布式回调能力。

</code_context>

<deferred>
## Deferred Ideas

- 完整的主题深度定制将推迟至后续业务需要时。
- 复杂的前端权限或更高级的多维检索筛选不包含在本轮的纯 UI 替换中。

</deferred>

---

*Phase: 10-element-plus-element-plus*
*Context gathered: 2026-03-31*
