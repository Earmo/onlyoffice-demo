---
phase: 04-document-library-experience
plan: 02
subsystem: editor-navigation-and-shell
tags: [editor-page, navigation, router, onlyoffice, switch-confirm]
requires:
  - phase: 04-01
    provides: 工作台首页与文档列表入口
provides:
  - `/editor/:id` 独立编辑页与 ONLYOFFICE 宿主边界
  - 从列表进入编辑页和返回工作台的明确导航链路
  - 编辑页内切换文档时的显式确认语义
affects: [04-03, phase-05]
tech-stack:
  added: [editor-shell, editor-page-route]
  patterns: [workspace-to-editor-flow, explicit-back-entry, confirm-before-switch]
key-files:
  created:
    - packages/web/src/pages/DocumentEditorPage.vue
    - packages/web/src/components/editor/EditorShell.vue
  modified:
    - packages/web/src/router/index.js
    - packages/web/src/pages/DocumentLibraryPage.vue
    - packages/web/src/App.vue
    - packages/web/src/style.css
key-decisions:
  - "编辑工作台独立为 /editor/:id 路由，首页与编辑页职责分开"
  - "编辑页必须始终提供返回文档列表入口，不能只依赖浏览器回退"
  - "在编辑页切换到另一份文档时先弹确认，不做无提示硬切换"
patterns-established:
  - "ONLYOFFICE 编辑器逻辑抽到 EditorShell，避免和路由状态继续耦在 App.vue"
  - "工作台和编辑页之间通过 documentId 导航，页面语义清晰可分享"
requirements-completed: [LIB-02]
duration: 35min
completed: 2026-03-25
---

# Phase 4 / Plan 02 Summary

**文档工作台和独立编辑页已经真正分开，列表进入编辑器、返回列表和切换文档确认都形成了清晰主流程。**

## Accomplishments

- 新增 `/editor/:id` 路由与 `DocumentEditorPage.vue`，编辑页不再和首页混在一个页面状态里。
- 旧 `App.vue` 中的 ONLYOFFICE 编辑器宿主逻辑已经抽到 `EditorShell.vue`，保留了 editor-config、保存状态、插图和只读切换等现有能力。
- 工作台列表项已经支持整行进入编辑页，编辑页顶部始终可见“返回文档列表”入口。
- 编辑页左侧补了最近文档切换区，切换到另一份文档时会先弹出确认提示，避免无提示离开当前文档。

## Execution Commits

- **实现提交：** `7c9698f` `feat(phase4): 落地文档工作台与编辑页流转`

## Notes

- 这一轮确认语义只基于当前页面状态，不强行接入复杂未保存检测。
- 编辑页仍保持 ONLYOFFICE 为核心舞台，左侧切换区只是辅助导航，不反向把首页职责拉回编辑页。

---
*Phase: 04-document-library-experience*
*Completed: 2026-03-25*
