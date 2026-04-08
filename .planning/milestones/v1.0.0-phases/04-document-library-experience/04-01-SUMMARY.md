---
phase: 04-document-library-experience
plan: 01
subsystem: library-home-and-query-contract
tags: [library-home, list-query, filters, dashboard, vue-router]
requires: []
provides:
  - 首页工作台所需的文档列表查询契约
  - 默认进入文档工作台的前端入口与路由壳层
  - 基于现有文档列表数据的最近文档区与空异常态骨架
affects: [04-02, 04-03, phase-05]
tech-stack:
  added: [vue-router, library-home-shell]
  patterns: [single-list-feed, dashboard-lite-home, actor-aware-list-response]
key-files:
  created:
    - packages/web/src/router/index.js
    - packages/web/src/pages/DocumentLibraryPage.vue
    - packages/web/src/components/library/DocumentList.vue
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentListResponse.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
    - packages/web/src/App.vue
    - packages/web/src/main.js
    - packages/web/src/style.css
key-decisions:
  - "首页默认先请求 /api/documents，而不是自动请求固定文档的 editor-config"
  - "搜索、排序和筛选继续复用 /api/documents 单接口，不另起第二套搜索 feed"
  - "最近文档区直接基于当前列表数据投影，不新增独立活动流接口"
patterns-established:
  - "工作台首页成为官方前端的默认入口，编辑器不再是根页面默认宿主"
  - "列表查询契约开始携带 tenant 与 actor 顶层上下文，方便首页直接消费"
requirements-completed: [LIB-01]
duration: 40min
completed: 2026-03-25
---

# Phase 4 / Plan 01 Summary

**首页已经从“默认直进编辑器”改成文档工作台入口，列表查询契约也补齐了搜索、排序和基础筛选语义。**

## Accomplishments

- `/api/documents` 现在支持 `query`、`status`、`sourceSystem`、`documentType`、`storage`、`sortDirection` 等工作台首页所需参数，且继续保留 `storageAvailable`、`status`、`actor`、`lastSavedTime` 等既有语义。
- `DocumentListResponse` 已新增 `tenantId`、`actorUser`、`actorName` 顶层字段，首页能直接展示当前租户与当前访问用户。
- 前端入口已经改为 `router + RouterView` 结构，`/` 默认加载 `DocumentLibraryPage.vue`，不再自动请求固定示例文档。
- 工作台首页已经具备轻量信息卡、顶部主操作区、最近文档区、主列表区域，以及列表局部错误态、空状态和无结果态骨架。

## Execution Commits

- **实现提交：** `7c9698f` `feat(phase4): 落地文档工作台与编辑页流转`

## Notes

- 这一轮只建立工作台首页与列表查询基础，不在这里引入独立活动流或完整检索系统。
- 搜索与筛选仍以文档进入流程为主，不会把首页扩成重型后台。

---
*Phase: 04-document-library-experience*
*Completed: 2026-03-25*
