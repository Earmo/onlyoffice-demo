---
phase: 05-distributed-editing-flow
plan: 01
subsystem: shared-runtime-state-and-event-stream
tags: [runtime-state, save-status, shared-db, event-stream, callback]
requires: []
provides:
  - 共享数据库驱动的编辑运行事件模型
  - 主表摘要状态与详细运行事件分层表达
  - save-status 的摘要加最近事件视图
affects: [05-02, 05-03, phase-06]
tech-stack:
  added: [document-runtime-event-table, runtime-event-repository, save-status-events]
  patterns: [shared-runtime-source-of-truth, summary-plus-recent-events, main-table-plus-event-stream]
key-files:
  created:
    - packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V4__create_document_runtime_event.sql
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentRuntimeEventEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentRuntimeEventRepository.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentSaveStatusEventResponse.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStatusService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentSaveStatusResponse.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStatusServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentMetadataServiceTest.java
key-decisions:
  - "save-status 的真相源迁到共享数据库，不再依赖实例内局部状态"
  - "文档主表继续承载摘要状态，完整运行事件单独进入事件表"
  - "编辑页轮询返回当前摘要状态加最近关键事件，而不是直接暴露完整事件流"
patterns-established:
  - "分布式运行态采用主表摘要加事件流分层，列表页与编辑页读取边界清晰"
  - "事件模型统一表达 callback、保存成功、保存失败和编辑初始化等关键运行节点"
requirements-completed: [EDIT-01, SAFE-03]
duration: 40min
completed: 2026-03-25
---

# Phase 5 / Plan 01 Summary

**编辑运行态已经从单实例内存语义升级为共享数据库语义，`save-status` 现在能稳定返回摘要状态和最近关键事件。**

## Accomplishments

- data 模块新增 `document_runtime_event` migration、entity、mapper 和 repository，运行事件已经有共享持久化落点。
- `DocumentStatusService` 已升级为共享运行态门面，`initialize`、`recordCallbackReceived`、`recordSaveSucceeded`、`recordSaveFailed` 和查询都围绕数据库工作。
- `DocumentSaveStatusResponse` 已补齐最近事件投影，编辑页轮询现在可以直接看到摘要状态、最近回调时间、最近保存时间和最近几条关键事件。
- `DocumentMetadataServiceTest`、`DocumentStatusServiceTest` 与 `DocumentControllerTest` 已覆盖共享状态投影和最近事件断言。

## Execution Commits

- **实现提交：** `24225fa` `feat(phase5): 落地分布式编辑运行链路`

## Notes

- 这一轮只把运行态真相源和事件流模型做扎实，不在这里扩成完整版本中心或完整审计中心。
- 列表页仍然只读文档主表摘要状态，事件流主要服务编辑页和运行态排障。

---
*Phase: 05-distributed-editing-flow*
*Completed: 2026-03-25*
