---
phase: 4
slug: document-library-experience
status: completed
created: 2026-03-25
sources:
  - .planning/phases/04-document-library-experience/04-CONTEXT.md
  - packages/web/package.json
  - packages/web/src/App.vue
  - packages/web/src/main.js
  - packages/web/src/style.css
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentSummaryResponse.java
---

# Phase 4 Research

## Research Question

Phase 4 需要回答的核心问题不是“再给当前编辑页塞一个列表面板”，而是“如何把现有单页编辑器宿主，重构成真正的文档工作台首页与独立编辑页，同时复用已经完成的文档 API、访问上下文和异常可见性语义”。

## Current Baseline

- 当前前端只有 `packages/web/src/App.vue` 一个根组件，页面一打开就会直接请求 `demo` 文档的 editor-config，上传、远程导入、只读切换和插图都挂在编辑器侧边控制台里。
- 前端尚未引入 `vue-router`，因此列表页与编辑页的 URL 语义尚不存在，也无法自然表达 `/` 和 `/editor/:id` 的职责分离。
- 后端已经有 `/api/documents`、`/api/documents/{id}`、`/api/documents/upload`、`/api/documents/import-remote` 这些接口，`DocumentSummaryResponse` 也已具备 `status`、`storageAvailable`、`actorUser`、`lastSavedTime` 等字段。
- Phase 3 已经让后端统一消费 `AccessContext`，意味着 Phase 4 前端不必重新设计身份输入，只需要把当前租户/用户上下文通过现有同源调用链自然呈现。
- 当前 `package.json` 没有测试命令，也没有前端测试框架，说明 Phase 4 的自动化验证主轴仍应以 `pnpm build` 和后端 MVC 测试为主，完整前端自动化测试属于 Phase 6。

## Recommended Technical Direction

### 1. Split the Frontend into Library Shell and Editor Route

最自然的前端演进方向是：

- `/`：文档工作台首页
- `/editor/:id`：独立编辑页

这与讨论阶段锁定的交互语义一致，也比在一个组件里堆越来越多状态更稳。实现层面最可能需要：

- 引入 `vue-router`
- 拆出 `DocumentLibraryPage`、`DocumentEditorPage`、若干列表/状态/入口组件
- 让根 `App.vue` 退回到路由壳层

### 2. Treat the Existing List API as the Source of Truth

当前后端文档列表接口已经足够成为 Phase 4 的真相源。前端的最近文档区、主列表、异常标签、最近保存时间等展示，都应优先基于 `DocumentListResponse` 投影，而不是再造第二套前端本地模型。

这意味着：

- “最近文档”只需要从列表结果中截取最近几条
- `storageAvailable=false`、`status=failed` 等状态直接使用现有字段投影
- 创建、上传、导入完成后也应回流到列表状态，而不是只更新编辑器局部状态

### 3. Search Should Be Added to the Existing List Endpoint, Not a New Feed

讨论阶段已经锁定“搜索走后端”。在现有结构下，最稳的方式是给 `/api/documents` 扩展查询参数，而不是新增单独的搜索端点。原因是：

- 当前列表、最近文档、异常标签都以同一份文档摘要为中心
- 如果新增另一套搜索接口，前端会出现两份列表模型，反而放大复杂度
- 后端已有 `DocumentMetadataService` 和 repository 边界，适合在现有查询链路上加条件

### 4. Multi-Dimensional Filters Need Guardrails

用户希望 Phase 4 支持多维筛选，但 context 同时又明确禁止把这一阶段做成“完整检索系统”。因此最合理的 guardrail 是：

- 只围绕已有摘要字段做筛选
- 优先支持最贴近工作台心智的几个维度，例如状态、异常、来源系统、文档类型
- 不在本阶段扩复杂组合规则、保存搜索、全文检索或高级查询 DSL

### 5. Create/Upload/Import Should Return to the Library First

虽然 roadmap 初始成功标准提到“上传后进入编辑流程”，但 discuss 结果进一步压实成了：

- 成功后先回到列表
- 高亮新建/上传/导入结果
- 再由用户明确进入编辑器

这个变化的价值在于：

- 它更符合“工作台入口”的产品心智
- 能避免前端在同一时刻同时处理创建成功、列表刷新、路由跳转、editor-config 初始化多重副作用
- 为后续异常提示、搜索定位和文档列表工作流留下更清晰的状态边界

### 6. Confirm Before Switching Documents in Editor Route

由于 ONLYOFFICE 编辑器切换文档并不是一个廉价操作，而当前页还有保存状态轮询、只读切换和插图等行为，因此从一个文档切到另一个文档时增加确认是合理的。

这意味着编辑页需要有：

- 当前文档上下文
- 目标文档上下文
- 切换确认 UI
- 返回列表入口

### 7. Keep the Home Page “Workbench-Lite”

讨论阶段已经明确：

- 有工作台感
- 但不能变成重型后台

因此首页应更像“轻量工作台”而不是“大而全仪表盘”：

- 顶部 1-2 个轻量信息卡
- 主操作区
- 最近文档小区域
- 主列表

不建议 Phase 4 就补：

- 独立活动流
- 复杂统计卡片
- 操作审计首页可视化

## Domain Findings

### The Current Single-File Frontend Is the Main Refactor Pressure

当前 `App.vue` 已同时承担：

- 编辑器加载
- 保存状态轮询
- 上传文档
- 远程导入
- 插图动作
- 控制台显示

这说明 Phase 4 真正的复杂度不在“列表接口有没有”，而在“如何拆开页面职责”。因此计划应该先明确页面和路由边界，再谈 UI 细节。

### Existing Summary Fields Already Support a Useful v1 Library

`DocumentSummaryResponse` 已提供：

- `title`
- `documentType`
- `status`
- `storageAvailable`
- `actorUser / actorName`
- `lastSavedTime`

这足以支撑一个第一版文档工作台首页，不需要 Phase 4 再追加一轮大范围 DTO 设计。

### Frontend Validation Must Stay Pragmatic in Phase 4

由于当前 web 模块没有 Vitest/Cypress/Playwright 等测试基础设施，Phase 4 计划中的自动验证不应假设前端测试会同步落地。更现实的组合是：

- `pnpm build`
- 后端 MVC 测试覆盖列表接口扩展
- 必要的手动验证清单

真正系统的前端自动化测试应放到 Phase 6。

## Rejected or Deferred Options

### Keep the List Inside the Existing Editor Console

不建议。这样只能得到“编辑页里塞一个列表抽屉”，达不到首页入口重构的目标。

### Build an Independent Activity Feed in Phase 4

不建议。当前需求只要求工作台感和最近文档，不需要新增活动流模型。

### Turn Search/Filter into a Full Retrieval System

不建议。Phase 4 可以支持后端搜索和多维筛选，但必须基于现有列表 API 的轻量扩展，不应引入复杂检索平台心智。

## Implementation Implications for Planning

Phase 4 最稳的拆法仍然是 3 个 plan、3 个 wave：

1. 先建立工作台首页壳层、列表 API 扩展点和轻量信息结构
2. 再拆分编辑页路由、列表进入链路和返回/切换确认
3. 最后打通新建/上传/导入回流列表、高亮结果以及空/错/筛选状态收口

这样拆的好处是：

- Wave 1 先完成“用户打开首页先看到什么”
- Wave 2 再完成“如何从列表稳定进入和切换编辑器”
- Wave 3 最后收口“如何从工作台创建新文档并看见结果”

## Validation Architecture

### Automated Focus

- `DocumentApiControllerTest`：列表查询参数、排序和筛选基础语义
- 前端 `pnpm build`：保证路由拆分和组件改造后仍能构建
- 若扩展了新的前端模块文件，至少要保证 Vite 构建链稳定

### Manual Focus

- 首页打开时默认显示工作台，而不是直接进入编辑器
- 空状态是否正确引导新建/上传
- 异常文档是否仍在主列表中可见
- 从列表进入编辑页、从编辑页返回列表、从编辑页切换文档是否符合确认语义
- 新建/上传/导入成功后是否先回流到列表并高亮结果

### Recommended Commands

- 前端构建验证：`cd packages/web && pnpm build`
- 后端接口验证：`cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test`
- 完整后端回归：`cd packages/server && mvn test`

## Planning Guardrails

- 不在本阶段引入独立活动流或审计首页
- 不在本阶段做完整文档检索平台
- 不把创建成功强制绑定成自动跳转编辑器
- 不破坏 Phase 2 建立的异常可见性和 Phase 3 建立的访问上下文语义

## Research Summary

最稳的路线是：

- 先把前端从单页编辑器宿主拆成“工作台首页 + 独立编辑页”
- 列表、最近文档和异常展示统一以现有 `/api/documents` 为真相源
- 搜索与筛选只作为现有列表接口的轻量扩展
- 新建、上传、导入完成后先回流到列表并高亮，再进入编辑器
- 用 `pnpm build + 后端 MVC 测试 + 手动验证清单` 作为当前阶段的现实验证组合
