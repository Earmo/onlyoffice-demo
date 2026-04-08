---
phase: 12-home-layout-delete-and-recent-edit-sorting
plan: 02
subsystem: web
tags: [layout, element-plus, homepage, cta]
requires:
  - phase: 10-element-plus-refactoring
    provides: Element Plus 页面结构基线
provides:
  - 首页左右栏调整为 1/5 : 4/5
  - 左右主盒子在桌面端都能自适应贴到底边
  - 开始编辑按钮与提示语层级按新视觉要求重排
affects: [document-library, document-list, homepage-layout]
tech-stack:
  added: []
  patterns: [双栏满高布局, CTA 与辅助文案分层]
key-files:
  created: []
  modified:
    - packages/web/src/pages/DocumentLibraryPage.vue
    - packages/web/src/components/library/DocumentList.vue
key-decisions:
  - "桌面端使用 5/19 栅格近似实现 1/5 : 4/5 比例"
  - "保持 Element Plus 卡片结构，但用页面级 flex 把左右主区域撑到底边"
  - "把“先查看，再决定是否进入编辑”降为辅助提示，与右侧说明保持同级文案样式"
patterns-established:
  - "工作台级布局由页面控制高度，列表组件只负责内容层"
requirements-completed: [PH12-LAYOUT-01]
duration: 40min
completed: 2026-03-31
---

# Phase 12 / Plan 02 Summary

**首页工作台的双栏比例和 CTA 层级已经按新的视觉要求收口完成。**

## Accomplishments

- `DocumentLibraryPage.vue` 把桌面端布局调整成 `5 / 19` 双栏比例，并通过 `flex` 让左右主区延伸到页面底边。
- 左侧最近文档卡片改成真正的自适应内容区，不再只是当前页数据切片后的静态块。
- `DocumentList.vue` 里“开始编辑”按钮移动到提示语上方，`先查看，再决定是否进入编辑` 改成和右侧说明一致的辅助文案样式。
- 列表列名从“最近保存”改成“最近编辑”，与新的后端字段和排序语义保持一致。

## Verification

- `cd packages/web && corepack pnpm test -- --run`
- `cd packages/web && corepack pnpm build`

## Notes

- 桌面端做满高，移动端则回退到自然流布局，避免小屏被强行撑高带来滚动体验问题。

---
*Phase: 12-home-layout-delete-and-recent-edit-sorting*
*Completed: 2026-03-31*
