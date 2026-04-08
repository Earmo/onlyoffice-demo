---
phase: 09-access-context-preview-and-editor-workbench
plan: 03
subsystem: editor-session-lifecycle-and-editing-state-convergence
tags: [editing-session, runtime-state, status, list]
requires: [09-01, 09-02]
provides:
  - 显式编辑会话开始/结束链路
  - 活跃编辑会话持久化表与 repository
  - 列表编辑中状态按活跃会话收敛
key-files:
  created:
    - packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V5__create_document_editor_session.sql
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentEditorSessionEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/mapper/DocumentEditorSessionMapper.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentEditorSessionRepository.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStatusService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStatusServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentMetadataServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStatusServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
key-decisions:
  - "运行事件流继续负责发生过什么，活跃编辑会话表负责现在是否有人正在编辑"
  - "返回列表、切换文档和离开编辑页都要显式结束当前编辑会话"
  - "列表页仍然读取摘要数据，只在服务端投影时拼入活跃编辑人数"
requirements-completed: [EDIT-03]
completed: 2026-03-27
---

# Phase 9 / Plan 03 Summary

**编辑会话现在有了显式生命周期，列表上的“编辑中”状态也已经改成以活跃编辑用户为真相源，不再因为旧运行事件滞留。**

## Accomplishments

- data 模块新增 `document_editor_session` migration、entity、mapper 和 repository，活跃编辑用户已经有独立持久化落点。
- `DocumentStatusServiceImpl` 已支持 `openEditingSession`、`closeEditingSession` 和活跃编辑人数统计，运行事件与会话摘要职责清晰分层。
- `DocumentController` 新增显式结束编辑会话接口；编辑器配置接口在预览与编辑两种语义下分别走 `initialize` 与 `openEditingSession`。
- `DocumentApiController` 在列表和详情投影时会拼入活跃编辑人数，当没有活跃编辑用户时会把 `editing` 收口回真实摘要状态。
- 编辑页返回列表或切换文档时都会先结束当前会话，前后端测试已经覆盖这条离开编辑页的显式清理路径。

## Execution Commits

- **实现提交：** `8cbb58e` `feat(phase9): 落地预览模式与编辑会话收敛`

## Notes

- 这轮会话模型只处理“谁还在编辑”，没有把 Phase 9 扩成完整版本中心；运行事件流依然主要服务保存状态与排障。

---
*Phase: 09-access-context-preview-and-editor-workbench*
*Completed: 2026-03-27*
