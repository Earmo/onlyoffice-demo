---
phase: 11-document-id-session-exit-and-list-pagination
plan: 02
subsystem: ui
tags: [pagination, document-list, element-plus, vitest]
requires:
  - phase: 11-01
    provides: 统一的内部 documentId 规则
provides:
  - 后端分页文档列表契约
  - 前端服务端分页消费
  - Vitest 可稳定加载 Element Plus 组件
affects: [document-library, document-api, repository]
tech-stack:
  added: []
  patterns: [后端分页为准, storage 过滤仍在后端完成]
key-files:
  created: []
  modified:
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepository.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentListResponse.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentMetadataServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
    - packages/web/src/pages/DocumentLibraryPage.vue
    - packages/web/src/test/DocumentLibraryPage.test.js
    - packages/web/vite.config.js
key-decisions:
  - "storage=all 走数据库分页快路径，storage=available/unavailable 继续在后端探测与切页"
  - "前端筛选项不再从当前页 documents 推导，固定枚举由页面层维护"
  - "Vitest 下关闭 Element Plus 样式自动导入并 inline 依赖，避免 base.css 阻塞测试"
patterns-established:
  - "列表页始终带 pageNumber/pageSize 请求后端"
requirements-completed: [PH11-LIST-01, LIB-01, LIB-02, QUAL-02]
duration: 55min
completed: 2026-03-31
---

# Phase 11 / Plan 02 Summary

**文档工作台现在已经切到真正的服务端分页模型，列表总数、筛选语义和当前页数据都以后端响应为准。**

## Accomplishments

- `DocumentMetadataRepository` 新增分页查询路径，`query/status/sourceSystem/documentType/sortDirection` 已下沉到数据库层。
- `DocumentApiController` 为列表响应补上 `pageNumber/pageSize/total/totalPages`，并在 `storage` 特殊过滤场景下继续保证 total 与当前页结果一致。
- `DocumentLibraryPage.vue` 改成显式维护分页状态，使用 Element Plus `el-pagination` 按页拉取 `/api/documents`。
- 状态、文档类型等筛选选项不再从当前页数据推导，避免前端把“当前页结果”误当成“租户全集”。
- `vite.config.js` 已修复 Vitest 下的 Element Plus CSS 导入问题，文档列表页测试可以稳定运行。

## Verification

- `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentApiControllerTest,DocumentStatusServiceTest,AccessContextErrorHandlingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `cd packages/web && corepack pnpm exec vitest run src/test/DocumentLibraryPage.test.js`

## Notes

- `sourceSystem` 过滤改成独立输入值，避免下拉项只反映当前页已加载文档。

---
*Phase: 11-document-id-session-exit-and-list-pagination*
*Completed: 2026-03-31*
