---
phase: 15-ai-writeback
plan: 03
subsystem: editor-shell
tags: [vue, onlyoffice, writeback, pinia, vitest]

requires:
  - phase: 15-ai-writeback
    provides: insertHtml bridge method and writeBackStore feedback contract
provides:
  - EditorShell insert-html event handling through onlyofficeBridge.insertHtml
  - saveStatus injection into EditorAiWorkbench runtimeContext
  - Shell-level writeback status propagation through writeBackStore
affects: [editor-ai-workbench, onlyoffice-writeback]

tech-stack:
  added: []
  patterns:
    - Shell handles bridge side effects and reports writeback results through Pinia store
    - Workbench emits insert-html while EditorShell owns ONLYOFFICE bridge calls

key-files:
  created:
    - .planning/phases/15-ai-writeback/15-03-SUMMARY.md
  modified:
    - packages/web/src/components/editor/EditorShell.vue
    - packages/web/src/test/EditorShell.test.js

key-decisions:
  - "EditorShell imports writeBackStore through the repository's existing relative import style because Vite has no @ alias configured."
  - "EditorShell tests mock writeBackStore to isolate shell bridge behavior from concurrent Plan 15-02 store implementation changes."

patterns-established:
  - "handleInsertHtml uses a double bridge guard before writeback: bridge instance plus bridgeReady."
  - "Shell-level writeback feedback is store-only; user messages are left to the workbench layer."

requirements-completed: [WRIT-01, WRIT-02]

duration: 6 min
completed: 2026-04-27
---

# Phase 15 Plan 03: EditorShell Writeback Summary

**EditorShell writeback bridge handler with store-based feedback and save status runtime injection**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-27T03:01:12Z
- **Completed:** 2026-04-27T03:07:22Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `runtimeContext.saveStatus` so `EditorAiWorkbench` can render the current save state from the shell.
- Added `handleInsertHtml({ html })`, which guards bridge readiness, calls `onlyofficeBridge.insertHtml(html)`, and updates `writeBackStore` on success or error without calling `ElMessage`.
- Bound `@insert-html="handleInsertHtml"` on `EditorAiWorkbench`.
- Removed the empty `console-panel-header` block and its dedicated CSS.
- Added explicit `handleInsertHtml` tests for bridge-not-ready, success, and failure paths.

## Task Commits

1. **Task 1: 注入 saveStatus + handleInsertHtml + 模板绑定 + 移除空 header** - `6ed33bb` (`feat`)
2. **Task 2: 在 EditorShell.test.js 新增 handleInsertHtml 显式测试块** - `df6ee70` (`test`)

## Files Created/Modified

- `packages/web/src/components/editor/EditorShell.vue` - Adds writeback handler, saveStatus runtime context, insert-html binding, and removes empty header markup/CSS.
- `packages/web/src/test/EditorShell.test.js` - Adds shell-level writeback tests with store mocking for bridge-not-ready, success, and error paths.
- `.planning/phases/15-ai-writeback/15-03-SUMMARY.md` - Execution summary and verification record.

## Decisions Made

- Used a relative store import in `EditorShell.vue` because `packages/web/vite.config.js` does not configure an `@` alias; keeping `@/stores/writeBackStore` caused Vite import resolution failure.
- Kept `EditorShell.test.js` focused on shell behavior by mocking the store module, since Plan 15-02 owns store internals and was modified concurrently.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used relative writeBackStore import instead of @ alias**
- **Found during:** Task 1
- **Issue:** The plan-specified `@/stores/writeBackStore` import failed Vite resolution because the web Vite config has no `@` alias.
- **Fix:** Imported `../../stores/writeBackStore.js` from `EditorShell.vue`, matching existing relative import conventions.
- **Files modified:** `packages/web/src/components/editor/EditorShell.vue`
- **Verification:** `corepack pnpm --dir packages/web exec vitest run src/test/EditorShell.test.js -t 'handleInsertHtml' --reporter=verbose`
- **Committed in:** `6ed33bb`

---

**Total deviations:** 1 auto-fixed (Rule 3 blocking)
**Impact on plan:** The store contract and runtime behavior remain unchanged; only the import path was adapted to the repository's current Vite setup.

## Issues Encountered

- `corepack pnpm --dir packages/web test -- src/test/EditorShell.test.js --reporter=verbose` still exits non-zero because the package script forwards arguments in a way that runs the broader suite and because two pre-existing `EditorShell.test.js` SSE/heartbeat assertions fail:
  - `应在 SSE healthy 时停止 save-status polling，但继续 heartbeat 续期并消费 runtime-events`
  - `应在 clean completion 后立即重连而不重新激活 REST fallback polling`
- The new `handleInsertHtml` block passed independently: 3 passed, 12 skipped with the scoped `-t 'handleInsertHtml'` run.

## Verification

- PASS: `corepack pnpm --dir packages/web exec vitest run src/test/EditorShell.test.js -t 'handleInsertHtml' --reporter=verbose`
  - `handleInsertHtml`: bridge not ready, success, and failure paths all passed.
- PARTIAL: `corepack pnpm --dir packages/web test -- src/test/EditorShell.test.js --reporter=verbose`
  - New `handleInsertHtml` tests passed.
  - Existing two SSE/heartbeat assertions failed as listed above.

## Known Stubs

None. Stub scan found only legitimate runtime initializers, test helpers, and reset values.

## Threat Flags

None. The only new trust-boundary behavior is the planned `EditorAiWorkbench` emit to `EditorShell.handleInsertHtml` and the planned bridge writeback call.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 15-04 can treat `EditorShell` as the host-side writeback endpoint: workbench emits sanitized HTML, shell calls the ONLYOFFICE bridge, and writeback feedback flows through `writeBackStore`.

## Self-Check: PASSED

- FOUND: `packages/web/src/components/editor/EditorShell.vue`
- FOUND: `packages/web/src/test/EditorShell.test.js`
- FOUND: `.planning/phases/15-ai-writeback/15-03-SUMMARY.md`
- FOUND commit: `6ed33bb`
- FOUND commit: `df6ee70`

---
*Phase: 15-ai-writeback*
*Completed: 2026-04-27*
