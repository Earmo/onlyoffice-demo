---
phase: 09-access-context-preview-and-editor-workbench
plan: 02
subsystem: preview-mode-and-editor-workbench-layout
tags: [preview, editor, workspace, console, layout]
requires: [09-01]
provides:
  - 文档预览页与编辑页双入口
  - 固定控制台与编辑器同层工作台布局
  - 前端页面级回归测试
affects: [09-03]
key-files:
  created:
    - packages/web/src/pages/DocumentPreviewPage.vue
    - packages/web/src/test/DocumentPreviewPage.test.js
  modified:
    - packages/web/src/router/index.js
    - packages/web/src/components/library/DocumentList.vue
    - packages/web/src/pages/DocumentLibraryPage.vue
    - packages/web/src/pages/DocumentEditorPage.vue
    - packages/web/src/components/editor/EditorShell.vue
    - packages/web/src/test/DocumentLibraryPage.test.js
    - packages/web/src/test/DocumentEditorPage.test.js
    - packages/web/src/test/EditorShell.test.js
key-decisions:
  - "列表入口正式拆分为查看文件与编辑文档"
  - "预览页使用只读 editor-config，不再在编辑页里内嵌只读切换"
  - "控制台由遮罩抽屉改为同层固定栏，展开时通过布局挤压编辑器"
requirements-completed: [LIB-04, UI-01]
completed: 2026-03-27
---

# Phase 9 / Plan 02 Summary

**前端已经从“单一打开文档”路径升级成“预览页 + 编辑工作台”双入口，编辑页的控制台也收口为固定同层布局。**

## Accomplishments

- 路由新增 `/preview/:documentId`，列表页和最近文档区已显式区分 `查看文件` 与 `编辑文档` 两种动作。
- `DocumentPreviewPage` 采用只读 `editor-config`，不建立编辑会话，也不再暴露编辑控制台。
- `DocumentEditorPage` 现在具备固定顶部工具区、可收起提示区和右侧 sticky 文档栏，页面结构已从临时调试壳升级成稳定工作台。
- `EditorShell` 已重构为固定控制台与编辑器同层布局，移除了旧的遮罩抽屉和“切换为只读/可编辑”按钮。
- 前端回归测试已经覆盖：列表双入口、预览页路由、编辑页返回/切换流程、提示区收起，以及 `EditorShell` 的预览/编辑差异。

## Execution Commits

- **实现提交：** `8cbb58e` `feat(phase9): 落地预览模式与编辑会话收敛`

## Notes

- Phase 9 的预览页重点是“安全查看”而不是“在编辑页里切成 readonly”，因此页面语义和运行态都已和编辑工作台明确分离。

---
*Phase: 09-access-context-preview-and-editor-workbench*
*Completed: 2026-03-27*
