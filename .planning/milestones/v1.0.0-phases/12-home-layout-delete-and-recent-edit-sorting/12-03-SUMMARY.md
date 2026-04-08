---
phase: 12-home-layout-delete-and-recent-edit-sorting
plan: 03
subsystem: integration
tags: [recent-documents, delete-flow, pagination, tests]
requires:
  - phase: 12-home-layout-delete-and-recent-edit-sorting
    provides: 删除接口、recent 真相源与新首页布局
provides:
  - 前端最近文档改为独立后端请求
  - 删除后列表、高亮和 recent 一起刷新
  - 页面与组件回归测试覆盖新链路
affects: [document-library, recent-documents, frontend-tests]
tech-stack:
  added: []
  patterns: [列表与 recent 双刷新, 删除后高亮清理, 组件级 table stub 测试]
key-files:
  created:
    - packages/web/src/test/DocumentList.test.js
  modified:
    - packages/web/src/pages/DocumentLibraryPage.vue
    - packages/web/src/test/DocumentLibraryPage.test.js
    - packages/web/src/components/library/DocumentList.vue
key-decisions:
  - "删除后统一重新拉取列表和 recent，而不是在前端做局部数组裁剪"
  - "如果删除的是当前高亮文档，立即清空 route query 里的 highlight"
  - "列表组件单测使用轻量 table/card/button stubs，避免被 Element Plus 内部实现波动干扰"
patterns-established:
  - "recent 文档与主列表共享后端真相源，但各自独立请求"
requirements-completed: [PH12-DELETE-01, PH12-RECENT-01, PH12-LAYOUT-01]
duration: 40min
completed: 2026-03-31
---

# Phase 12 / Plan 03 Summary

**最近文档、删除刷新和前端回归测试已经接成完整闭环。**

## Accomplishments

- `DocumentLibraryPage.vue` 改成启动时并行拉取列表和 recent，删除后会同步刷新两边数据。
- 删除当前高亮文档时会主动清理 `highlight` 路由参数，避免 UI 继续指向已删除文档。
- `DocumentLibraryPage.test.js` 新增 recent 接口、删除刷新和高亮清理回归。
- `DocumentList.test.js` 覆盖了 CTA 层级和预览/编辑/删除事件发射行为。

## Verification

- `cd packages/web && corepack pnpm test -- --run`
- `cd packages/web && corepack pnpm build`

## Notes

- 组件测试里的自定义 `ElTable` / `ElTableColumn` stub 只用于稳定渲染业务插槽，不改变生产代码路径。

---
*Phase: 12-home-layout-delete-and-recent-edit-sorting*
*Completed: 2026-03-31*
