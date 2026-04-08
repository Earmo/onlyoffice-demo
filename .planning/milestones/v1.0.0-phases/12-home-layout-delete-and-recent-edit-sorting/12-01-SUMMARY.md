---
phase: 12-home-layout-delete-and-recent-edit-sorting
plan: 01
subsystem: backend
tags: [logical-delete, recent-documents, last-edited-time, archived]
requires:
  - phase: 11-document-id-session-exit-and-list-pagination
    provides: 后端分页列表与编辑状态收敛基线
provides:
  - 默认列表和 recent 接口统一排除 archived 文档
  - 文档支持逻辑删除并记录审计事件
  - 标准详情、预览、编辑、下载、save-status 入口拒绝访问 archived 文档
affects: [document-api, metadata, runtime, audit]
tech-stack:
  added: []
  patterns: [逻辑删除复用 archived, 最近编辑时间后端投影, 独立 recent 接口]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentOperationConflictException.java
  modified:
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepository.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentSummaryResponse.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentMetadataServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStorageServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/GlobalExceptionHandler.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
key-decisions:
  - "逻辑删除直接复用 metadata.status = archived，不做物理删除"
  - "最近文档使用独立 /api/documents/recent 真相源，不再受当前页分页和筛选影响"
  - "archived 文档统一按不可访问处理，标准入口返回 not found 语义"
patterns-established:
  - "用户侧可见文档查询默认排除 archived"
requirements-completed: [PH12-DELETE-01, PH12-RECENT-01]
duration: 40min
completed: 2026-03-31
---

# Phase 12 / Plan 01 Summary

**后端现在已经把逻辑删除、最近文档真相源和最近编辑时间投影统一收口到同一套契约里。**

## Accomplishments

- `DocumentMetadataRepository` 新增活跃文档查询路径，默认列表和 `/api/documents/recent` 都会排除 `archived`。
- `DocumentSummaryResponse` 新增 `lastEditedTime`，前端可以直接显示和消费最近编辑时间。
- `DocumentApiController` 新增 `DELETE /api/documents/{documentId}` 和 `GET /api/documents/recent`，删除前会检查活跃编辑会话。
- `DocumentMetadataServiceImpl`、`DocumentStorageServiceImpl` 和运行时控制器链路统一拒绝访问 archived 文档。
- 删除动作会记录 `document_archived` 审计事件，相关后端回归测试已补齐。

## Verification

- `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataRepositoryTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,AccessAuditServiceTest,DocumentStorageServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

## Notes

- 删除冲突单独用 `DocumentOperationConflictException` 映射成 `409 Conflict`，避免误伤仍然需要返回 `500` 的运行时配置错误。

---
*Phase: 12-home-layout-delete-and-recent-edit-sorting*
*Completed: 2026-03-31*
