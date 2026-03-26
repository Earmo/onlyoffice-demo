---
phase: 6
slug: verification-and-delivery
status: completed
created: 2026-03-26
sources:
  - .planning/phases/06-verification-and-delivery/06-CONTEXT.md
  - packages/server/pom.xml
  - packages/web/package.json
  - README.md
  - docs/minimal-integration.md
  - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
  - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
  - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStatusServiceTest.java
  - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/StorageProviderResolverTest.java
  - packages/web/src/pages/DocumentLibraryPage.vue
  - packages/web/src/pages/DocumentEditorPage.vue
  - packages/web/src/components/editor/EditorShell.vue
---

# Phase 6 Research

## Research Question

Phase 6 需要回答的核心问题不是“再补几条测试”，而是“如何把当前已经能工作的前后端与文档服务链路，收口成一个可以被新接手的人稳定验证、按文档部署、按脚本回归的交付基线”，同时避免把这一阶段膨胀成完整 CI 平台或重型运维文档工程。

## Current Baseline

- 后端已经有较完整的 `Spring Boot Test + MockMvc + service` 测试基线，覆盖文档 API、ONLYOFFICE callback、存储策略、访问上下文和运行状态等关键路径。
- Phase 5 已经把高风险链路收口到少数服务：`OnlyofficeJwtService`、`OnlyofficeConfigService`、`DocumentStatusService`、`DocumentStorageService`、`OnlyofficeImageService`、`RemoteResourceSecurityService`。
- 前端目前只有 `dev/build/preview` 脚本，还没有正式测试框架和自动化测试命令。
- 根目录没有统一验证入口；当前现实验证路径仍然依赖人工记忆 `mvn test -q`、`corepack pnpm build`、`docker compose config` 的组合。
- 文档入口目前主要分散在 `README.md` 和 `docs/minimal-integration.md`，虽然已经能承接最小接入，但还没有形成“快速入口 + 交付说明 + 配置矩阵 + 验收清单”的清晰结构。

## Domain Findings

### 1. Backend Coverage Is Already Broad Enough to Focus on High-Risk Gaps

后端现有码并不是“没有测试”，而是“测试已经有了，但交付级回归网还没完全成型”：

- `DocumentControllerTest` 已能覆盖 `editor-config`、`callback`、`save-status`
- `DocumentApiControllerTest` 已覆盖列表、新建、上传、导入等工作台主路径
- `DocumentStatusServiceTest`、`DocumentStorageServiceTest`、`OnlyofficeConfigServiceTest`、`OnlyofficeImageServiceTest` 已覆盖一部分分布式和安全行为
- `StorageProviderResolverTest`、`LocalDocumentStorageStrategyTest`、`MinioDocumentStorageStrategyTest` 已经为 provider 层提供了起点

这说明 Phase 6 最稳的方向不是推翻重测，而是围绕 Phase 5 的高风险链路继续补 controller/service 级回归，同时顺手把 provider 扩展点的测试骨架稳定下来。

### 2. Frontend Has a Clear Testable Surface but No Test Harness Yet

前端已经在 Phase 4 形成了清晰的测试边界：

- `DocumentLibraryPage.vue` 承载工作台首页、列表加载、搜索筛选、创建入口和结果回流
- `DocumentEditorPage.vue` 承载编辑页路由和返回工作台语义
- `EditorShell.vue` 承载编辑器配置获取、保存状态区、文档切换确认等核心页面行为

这类页面非常适合用 `Vitest + Vue Test Utils` 建立稳定回归基线，因为：

- 不需要真实嵌入 ONLYOFFICE iframe
- 可以 mock `editor-config` 和 API 返回
- 能把焦点放在列表入口、导航切换、保存状态显示和创建结果回流上

因此不建议在 Phase 6 直接跳到 Playwright 或真实 ONLYOFFICE 端到端嵌入验证。

### 3. Unified Verification Entry Is the Real QUAL-03 Gap

当前仓库已经有“可跑的命令”，但没有“可交付的入口”：

- 后端：`cd packages/server && mvn test`
- 前端：`cd packages/web && corepack pnpm build`
- 编排：`docker compose config`

这意味着 Phase 6 的关键价值之一，是把这些零散命令提升成根级、分层、可文档化的验证入口。与其引入复杂工具，不如优先收口成一组清晰的根级命令或脚本：

- 后端测试
- 前端测试
- 前端构建
- compose 配置校验
- 交付验证总入口

这样最符合“新接手的人按文档就能跑”的目标。

### 4. Delivery Docs Need Structure, Not Just More Text

`README.md` 和 `docs/minimal-integration.md` 已经提供了有效内容，但还缺三类交付材料：

- 面向仓库使用者的统一验证顺序
- 面向部署者的独立部署/微服务接入收口说明
- 面向交付验收的配置矩阵和人工验证清单

因此 Phase 6 更适合做“结构升级”而不是单纯继续堆长文：

- README 作为总入口
- docs 下补更清晰的交付/验证文档
- `minimal-integration.md` 保留最小接入定位

## Recommended Technical Direction

### 1. Split Phase 6 into Two Parallel Test Tracks Plus One Delivery Closure Track

最稳的拆法仍然是 3 个 plan：

1. 后端高风险回归测试与 provider 测试骨架
2. 前端 `Vitest + Vue Test Utils` 基线与工作台/编辑页回归测试
3. 根级统一验证入口、README/docs 交付结构、配置矩阵和简洁验收清单

其中：

- Wave 1 可以并行做后端测试和前端测试基线
- Wave 2 再做根级验证入口和交付文档收口

这样既能并行推进，也能保证文档里的命令最终引用的是已经落地的测试入口。

### 2. Keep Backend Tests Focused on Phase 5 Risk Surfaces

推荐后端回归重点集中在：

- callback JWT 拒绝与错误语义
- `save-status` 的最近事件投影和共享状态摘要
- `OnlyofficeConfigService` 的角色化 URL 与 fail-fast 行为
- 远程资源安全拒绝路径
- provider 路由分发和默认策略选择

不建议在 Phase 6 把 repository 层重新做成大量细粒度测试；当前 repository 已有必要覆盖，继续把收益集中在 service/controller 层更划算。

### 3. Use Component/Page Tests for Frontend Instead of Real Editor Integration

前端测试建议采用：

- `Vitest`
- `@vue/test-utils`
- 必要时再补 `jsdom`

测试重点：

- 工作台首页初次加载和空/错状态
- 搜索/筛选/高亮结果回流
- 创建入口事件派发和页面状态反应
- 编辑页返回列表、切换确认、保存状态区渲染
- `EditorShell` 对 mocked `editor-config` 和 `save-status` 的渲染行为

这能以较低成本建立稳定回归基线，同时避免真实 ONLYOFFICE 集成带来的脆弱测试。

### 4. Prefer Root-Level Layered Verification Commands Over One Big Script

讨论阶段已经锁定“根级统一入口 + 分层命令”。最自然的设计是：

- 根级入口只负责协调，不替代子模块原生命令
- 保留模块内命令，同时在根级提供：
  - server test
  - web test
  - web build
  - compose verify
  - delivery verify

具体载体可以是：

- 根 `package.json`
- 根脚本文件
- 或两者组合

但重点不是工具选型，而是把验证入口从“隐性知识”变成“显式命令集”。

### 5. Treat Docs as an Executable Delivery Surface

Phase 6 的文档应该服务于执行，而不是只服务阅读。推荐收口到：

- README：快速入口、推荐执行顺序、文档导航
- `docs/minimal-integration.md`：继续做最小接入说明
- 新增交付/验证文档：独立部署、微服务接入、配置矩阵、人工验收清单

如果 Phase 6 做得好，用户不应该再需要通过翻 `.planning` 才知道怎么验证或交付这个服务。

## Validation Architecture

### Automated Focus

- 后端：
  - `DocumentControllerTest`
  - `DocumentApiControllerTest`
  - `DocumentStatusServiceTest`
  - `OnlyofficeConfigServiceTest`
  - `DocumentStorageServiceTest`
  - `OnlyofficeImageServiceTest`
  - `StorageProviderResolverTest`
- 前端：
  - 工作台首页页面测试
  - 编辑页和 `EditorShell` 页面/组件测试
- 统一验证：
  - 根级命令串联后端测试、前端测试/构建、compose 配置校验

### Manual Focus

- 按 README/交付文档给出的顺序，新接手的人是否能从根目录完成一次完整验证
- 独立部署说明和微服务接入说明是否足以支撑本地联调
- 配置矩阵是否能清楚区分必填项、默认值和适用场景

### Recommended Commands

- 后端完整回归：`cd packages/server && mvn test`
- 前端构建回归：`cd packages/web && corepack pnpm build`
- 前端测试基线：`cd packages/web && corepack pnpm test -- --run`
- 编排校验：`docker compose config`
- 根级统一验证：由 Phase 6 落地具体命令，但应覆盖以上四类能力

## Planning Guardrails

- 不在本阶段引入真实多实例容器自动化测试平台
- 不在本阶段引入 Playwright 级 ONLYOFFICE 真实嵌入验收
- 不把交付文档扩成完整运维手册或 SRE 手册
- 不为了统一命令而破坏当前 `packages/server` 与 `packages/web` 的原生命令边界
- 不把前端测试做成真实文档服务器依赖型测试

## Research Summary

最稳的路线是：

- 用已有后端测试基线补 Phase 5 高风险链路回归，并把 provider 扩展点测试骨架稳定下来
- 为 Vue 工作台首页和编辑页补 `Vitest + Vue Test Utils` 页面级回归测试
- 在仓库根提供分层统一验证入口，继续复用 `mvn test`、`pnpm build/test` 和 `docker compose config`
- 把 README 和 docs 收口成“快速入口 + 最小接入 + 交付/验证说明 + 配置矩阵”的结构化交付面
