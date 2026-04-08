---
phase: 06-verification-and-delivery
plan: 02
subsystem: frontend-regression-baseline
tags: [frontend-tests, vitest, vue-test-utils, workbench, editor]
requires: []
provides:
  - Vue 前端正式测试命令
  - 工作台首页与编辑页页面级回归测试
  - ONLYOFFICE mock 驱动的稳定前端测试基线
affects: [phase-04, phase-06]
tech-stack:
  added: [vitest, vue-test-utils, jsdom, frontend-test-helpers]
  patterns: [mock-editor-runtime, page-level-regression, shared-test-setup]
key-files:
  created:
    - packages/web/src/test/setup.js
    - packages/web/src/test/helpers.js
    - packages/web/src/test/DocumentLibraryPage.test.js
    - packages/web/src/test/DocumentEditorPage.test.js
    - packages/web/src/test/EditorShell.test.js
  modified:
    - packages/web/package.json
    - packages/web/vite.config.js
    - packages/web/pnpm-lock.yaml
key-decisions:
  - "前端测试先聚焦工作台与编辑页状态流转，不追真实 ONLYOFFICE iframe 集成"
  - "Vitest + Vue Test Utils 作为 Phase 6 的正式页面级回归基线"
patterns-established:
  - "工作台首页、编辑页和保存状态组件都能在 mock fetch / mock router / mock editor 组合下稳定回归"
  - "前端测试与生产构建可以共存，不需要真实 ONLYOFFICE 运行时服务"
requirements-completed: [QUAL-02]
duration: 45min
completed: 2026-03-26
---

# Phase 6 / Plan 02 Summary

**官方前端已经拥有正式自动化测试入口，文档工作台和编辑页的核心流转不再只有构建级冒烟保护。**

## Accomplishments

- `packages/web/package.json` 新增 `test` 与 `test:watch` 脚本，正式把 Vitest 纳入前端命令面。
- `packages/web/vite.config.js` 已接入 `jsdom`、`globals` 和统一 `setupFiles`，当前 Vite + Vue 3 结构可以直接跑页面测试。
- 新增 `src/test/setup.js` 与 `src/test/helpers.js`，把 `fetch`、`alert` 和 mock 响应收口为轻量复用基建。
- `DocumentLibraryPage.test.js` 覆盖了列表加载、上下文展示、结果高亮和新建回流列表行为。
- `DocumentEditorPage.test.js` 与 `EditorShell.test.js` 覆盖了返回列表、切换确认、editor-config 加载、保存状态区与最近事件展示。

## Execution Notes

- `corepack pnpm test -- --run` 已通过，当前前端共有 3 个测试文件、5 条页面级测试全部通过。
- `corepack pnpm build` 也已通过，说明测试配置没有破坏现有生产构建链路。

## Notes

- 页面测试统一采用 mock `editor-config` 和 mock API 数据，不依赖真实 ONLYOFFICE iframe。
- 首页请求断言已调整为匹配运行时 API 目标，不再把绝对 URL 和相对 URL 的差异误判成失败。

---
*Phase: 06-verification-and-delivery*
*Completed: 2026-03-26*
