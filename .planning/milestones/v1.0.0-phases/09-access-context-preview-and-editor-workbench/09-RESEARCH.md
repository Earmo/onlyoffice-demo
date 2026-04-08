---
phase: 9
slug: access-context-preview-and-editor-workbench
status: completed
created: 2026-03-27
sources:
  - .planning/ROADMAP.md
  - .planning/REQUIREMENTS.md
  - .planning/STATE.md
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/AccessContextProvider.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/AccessContextResolver.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStatusServiceImpl.java
  - packages/web/src/router/index.js
  - packages/web/src/pages/DocumentLibraryPage.vue
  - packages/web/src/pages/DocumentEditorPage.vue
  - packages/web/src/components/editor/EditorShell.vue
  - packages/web/src/components/library/DocumentList.vue
---

# Phase 9 Research

## Research Question

Phase 9 需要解决的不是单点 UI 微调，而是 3 条已经开始显露边界问题的链路：

1. 访问上下文虽然已经有 `provider SPI`，但还没有被正式收口成“策略模式 + 内置策略 + 自定义扩展契约”的稳定产品能力；
2. 列表页和编辑页已经分开，但当前仍然只有“打开文档进入编辑”一条主路径，没有“只读预览”入口；
3. 编辑页的运行态仍偏 demo 形态，控制台是抽屉遮罩，顶部提示区不可收起，也没有“离开编辑页就结束会话”的明确语义。

因此 Phase 9 的核心问题是：如何在不推翻 Phase 3、4、5 已有基线的前提下，把访问上下文、预览模式和编辑工作台的运行语义一起收口成下一阶段可持续演进的基线。

## Current Baseline

- 后端已经存在访问上下文 SPI 基线：
  - `AccessContextProvider` 是稳定接口；
  - `AccessContextResolver` 会按配置顺序聚合 provider；
  - 现有内置实现至少包括 `header`、`jwt`、`default`。
- 这意味着 `USER-04` 不应该被理解成“从零发明策略模式”，而应该被理解成：
  - 统一命名与职责边界；
  - 明确内置策略和自定义扩展的注册契约；
  - 把现有解析链正式提升为可交付的策略体系。
- 前端当前只有两套路由：
  - `/`：工作台首页；
  - `/editor/:documentId`：独立编辑页。
- 当前列表页只有“打开文档”入口，`DocumentList.vue` 没有区分“预览”和“编辑”。
- 当前编辑页 `EditorShell.vue` 仍保留 `readonly` 切换按钮，并通过重新请求 `editor-config?readonly=true|false` 切换模式。
- 当前控制台是“按钮 + 抽屉 + 背景遮罩”模式，不是固定并列布局。
- 当前 `DocumentStatusServiceImpl.initialize()` 只记录 `editor_opened` 运行事件，并不会建立显式的“活跃编辑会话”真相源；主表 `editing` 更接近 callback / 保存过程状态，而不是“当前有用户正在编辑”。

## Domain Findings

### 1. AccessContext Is Already SPI-First, But Not Yet Productized as a Strategy Contract

`AccessContextProvider` + `AccessContextResolver` 已经说明仓库在技术实现上先走了一步。真正缺的不是再造一个接口，而是把下列事项正式固化：

- “header / jwt” 是 starter 自带的内置解析策略；
- `default` 是补齐策略，不应与显式解析策略混为同一语义；
- 自定义 provider 的覆盖顺序、命名约束、启用方式和错误语义，需要补成测试和文档都能稳定消费的契约。

换句话说，Phase 9 的 `USER-04` 更像“策略体系定稿”，而不是“策略体系初建”。

### 2. Preview Mode Needs Its Own Product Semantics

当前系统虽然技术上已经支持 `editor-config?readonly=true`，但产品层还没有真正的“预览模式”：

- 列表页没有独立的预览入口；
- 路由没有 `/preview/:documentId` 一类的独立页面；
- 编辑页里仍允许用户自己切到只读，这会混淆“预览页”和“编辑页”的职责边界。

因此更合理的目标状态是：

- 列表页显式区分“查看文件”和“编辑文档”；
- 预览页以只读方式打开，强调“查看”；
- 编辑页固定为可编辑工作台，不再内置只读开关。

### 3. Editor Workbench Layout Is Still Overlay-Oriented

当前 `EditorShell.vue` 的控制台抽屉更像“临时调试面板”，而不是长期使用的编辑工作台。具体表现为：

- 控制台以遮罩方式覆盖页面；
- 顶部提示区域不能折叠；
- 编辑器、控制台和提示区缺少统一布局骨架；
- “固定顶部工具栏、固定右侧标题类栏”的工作台需求还没有形成明确容器层级。

这意味着 Phase 9 的 `UI-01` 应该聚焦布局体系，而不是停留在单个按钮的显隐切换。

### 4. Editing Status Needs a Session Truth, Not Just Runtime Events

Phase 5 已建立“运行事件流 + 摘要状态”，它适合承接：

- callback 到达；
- 保存成功 / 失败；
- 最近运行事件回放。

但用户现在提出的新语义是：

- 返回列表或切换文档时，要结束当前编辑会话；
- 当没有活跃编辑用户时，列表不应继续显示“编辑中”。

这说明现有 `DocumentStatusServiceImpl` 还缺一个“活跃编辑会话/用户”层。仅靠 `editor_opened` 事件或 callback 状态，无法可靠表达“现在是否仍有人在编辑”。

### 5. Phase 9 Should Be Split Across Backend, Frontend, Then Lifecycle Convergence

最自然的拆法不是按“后端 / 前端 / UI”机械分层，而是按依赖关系拆成：

- 访问上下文策略体系收口；
- 预览模式与编辑工作台布局改造；
- 编辑会话结束与列表状态收敛。

原因是：

- 策略体系与预览路由可以并行推进；
- 会话结束与“编辑中”状态收敛需要同时依赖后端运行语义和前端离开行为，适合作为收尾计划。

## Recommended Technical Direction

### 1. Use Three Plans in Two Waves

推荐执行顺序：

- Wave 1
  - `09-01`：收口访问上下文策略与扩展契约
  - `09-02`：增加预览模式并重构编辑工作台布局
- Wave 2
  - `09-03`：增加编辑会话结束与列表状态收敛

这样既能并行推进，也能避免在会话收敛计划里反复返工前端页面结构。

### 2. Preserve the Existing Provider SPI and Promote It into a Stable Strategy Model

Phase 9 不应推翻 `AccessContextProvider`，而应沿着现有基线完成以下收口：

- 保留 provider 接口，明确它就是访问上下文策略接口；
- 把 `header`、`jwt` 作为官方内置策略；
- 把 `default` 明确为补齐策略或兜底策略；
- 允许更多自定义 provider 注入，但必须遵守稳定命名和顺序配置。

### 3. Separate Preview and Edit as Two Routes with Two Intentions

推荐的产品语义：

- `/preview/:documentId`
  - 用只读 editor-config 打开；
  - 入口文案是“查看文件”；
  - 页面不暴露“切换为可编辑”。
- `/editor/:documentId`
  - 固定为编辑工作台；
  - 入口文案改为“编辑文档”；
  - 页面里不再出现只读切换按钮。

这样可以让 `LIB-04` 的“预览”真正成为独立能力，而不是编辑页里的一个临时模式。

### 4. Introduce an Explicit Session Lifecycle

现有运行事件流要继续保留，但 Phase 9 需要新增会话生命周期能力：

- 进入编辑页或预览页时建立当前会话；
- 返回列表、切换文档、离开页面时显式结束会话；
- 列表上的“编辑中”应由活跃编辑会话摘要来决定，而不是仅由 callback 或旧状态残留决定。

这不等于完整协同系统，但足以把当前“编辑中”语义从“最近做过保存动作”纠正为“当前真的有人在编辑”。

### 5. Make Layout Changes Structural, Not Cosmetic

编辑页布局改造不应停留在样式补丁，而要建立清晰的同层结构：

- 顶部提示区：可收起；
- 主体左侧：编辑器容器；
- 主体右侧：固定控制台 / 标题类栏；
- 顶部工具区：固定；
- 编辑器可用空间由布局自然挤压，而不是通过遮罩层覆盖。

## Validation Architecture

Phase 9 的验证应继续沿用“后端 MVC + service、前端 Vitest 页面回归”的既有基线，不新增新的测试平台。

### Automated Focus

- 访问上下文策略与错误语义：
  - `AccessContextResolverTest`
  - `HeaderAccessContextProviderTest`
  - `JwtAccessContextProviderTest`
  - `CustomAccessContextProviderOverrideTest`
  - `AccessContextErrorHandlingTest`
- 预览模式与编辑入口：
  - `DocumentControllerTest`
  - `DocumentApiControllerTest`
  - `DocumentLibraryPage.test.js`
  - `DocumentEditorPage.test.js`
  - `EditorShell.test.js`
- 会话结束与编辑状态收敛：
  - 新增或扩展 `DocumentStatusServiceTest`
  - 新增或扩展 `DocumentControllerTest`
  - 新增前端离开页面 / 切换文档行为测试

### Manual Focus

- 预览页与编辑页的产品意图是否真正分开，而不是只换了按钮文案；
- 控制台固定布局是否在桌面端和移动端都还能正常工作；
- 返回列表、切换文档和关闭页面时，会话结束是否与列表“编辑中”状态一致收敛。

### Recommended Commands

- 后端访问上下文与运行态回归：
  - `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=AccessContextResolverTest,HeaderAccessContextProviderTest,JwtAccessContextProviderTest,CustomAccessContextProviderOverrideTest,AccessContextErrorHandlingTest,DocumentStatusServiceTest,DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 前端页面与组件回归：
  - `cd packages/web && corepack pnpm test -- --run`
- 前端构建回归：
  - `cd packages/web && corepack pnpm build`

## Planning Guardrails

- 不把 Phase 9 做成“访问上下文体系推倒重来”，要复用现有 `AccessContextProvider` 基线。
- 不在本阶段引入完整协同在线人数系统，只收口“活跃编辑会话”和列表 `editing` 语义。
- 不让预览模式重新依赖编辑页内部的只读切换按钮，而要形成独立入口和独立页面意图。
- 不把控制台布局改造做成纯样式修补，必须在结构层完成“同层并列、非遮罩”改造。
- 不把列表页状态完全改为直接读取运行事件流，列表仍优先读取摘要状态，只在必要处接入会话摘要结果。

## Research Summary

Phase 9 最稳的路线是：

- 基于现有 `AccessContextProvider` SPI，把访问上下文正式收口为“内置策略 + 自定义扩展”的稳定策略体系；
- 为文档列表新增独立预览入口，并把编辑页收束成纯编辑工作台；
- 把编辑页控制台和顶部提示区重构为固定布局的一部分；
- 新增显式会话结束语义，让列表上的“编辑中”真正代表仍有活跃编辑用户。
