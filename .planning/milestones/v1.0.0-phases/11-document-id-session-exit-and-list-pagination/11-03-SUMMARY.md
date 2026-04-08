---
phase: 11-document-id-session-exit-and-list-pagination
plan: 03
subsystem: editor-session
tags: [editor-exit, session-close, status-reconciliation, regression]
requires:
  - phase: 11-02
    provides: 后端分页列表与前端服务端拉取
provides:
  - 编辑页单次离开编排
  - close-session 请求防重
  - 列表/详情状态回归覆盖
affects: [editor-shell, editor-page, document-status]
tech-stack:
  added: []
  patterns: [leave guard, close-session request dedupe]
key-files:
  created: []
  modified:
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
    - packages/web/src/components/editor/EditorShell.vue
    - packages/web/src/pages/DocumentEditorPage.vue
    - packages/web/src/test/DocumentEditorPage.test.js
    - packages/web/src/test/EditorShell.test.js
    - packages/web/src/test/DocumentLibraryPage.test.js
key-decisions:
  - "页面级 `isLeaving` 负责串行化返回列表和切换文档动作"
  - "组件级 `closeEditingSessionPromise` 负责把重复 close 调用折叠成单请求"
  - "后端继续以 `activeEditingCount > 0 ? editing : entity.status` 作为唯一投影规则"
patterns-established:
  - "显式 close 成功后再导航，失败时停留当前页"
requirements-completed: [PH11-SESSION-01, EDIT-03, QUAL-02]
duration: 45min
completed: 2026-03-31
---

# Phase 11 / Plan 03 Summary

**离开编辑页现在被收口成一次性退出编排，列表和详情在最后一个活跃会话关闭后也不会再继续泄漏旧的 `editing` 状态。**

## Accomplishments

- `DocumentEditorPage.vue` 新增 `isLeaving`，返回列表和切换文档都会共用同一条“先 close，再导航”的流程。
- `EditorShell.vue` 新增 `closeEditingSessionPromise` 与 `isClosingSession`，重复离开不会再并发打两次 close-session。
- 后端测试补上“activeEditors=0 时列表/详情返回持久化状态而不是 editing”的回归断言。
- 前端测试补上返回列表、切换文档、重复离开和 close 失败留在当前页等关键回归场景。

## Verification

- `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStatusServiceTest,DocumentControllerTest,DocumentApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `cd packages/web && corepack pnpm exec vitest run src/test/DocumentEditorPage.test.js src/test/EditorShell.test.js src/test/DocumentPreviewPage.test.js`

## Notes

- 这轮没有改后端状态收敛主逻辑，只是把已有规则补成显式回归测试，并把真正的用户态问题收口在前端退出编排上。

---
*Phase: 11-document-id-session-exit-and-list-pagination*
*Completed: 2026-03-31*
