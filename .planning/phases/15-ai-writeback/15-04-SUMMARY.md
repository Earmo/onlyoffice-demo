---
phase: 15-ai-writeback
plan: 04
subsystem: e2e-validation
tags: [onlyoffice, writeback, smoke, human-verification]

requires:
  - phase: 15-ai-writeback
    provides: bridge/workbench/shell writeback implementation
provides:
  - Phase 15 automated regression result
  - human smoke checklist for final ONLYOFFICE validation
affects: [15-ai-writeback]

tech-stack:
  added: []
  patterns:
    - vitest focused phase regression
    - runtime-events save-status with independent editing-session heartbeat

key-files:
  created:
    - .planning/phases/15-ai-writeback/15-04-SUMMARY.md
  modified:
    - packages/web/src/components/editor/EditorShell.vue

key-decisions:
  - "SSE healthy state stops REST save-status polling but keeps editing-session heartbeat active every 5s."
  - "Manual ONLYOFFICE smoke remains required because document insertion, selection replacement, and bridge-not-ready behavior depend on a live editor runtime."

patterns-established:
  - "Runtime-events owns save status; heartbeat remains a separate session-liveness mechanism."

requirements-completed: [WRIT-01, WRIT-02]

duration: 10 min
completed: 2026-04-27
---

# Phase 15 Plan 04: Final Validation Summary

## Automated Checks

Passed:

```bash
corepack pnpm --dir packages/web exec vitest run src/test/onlyofficeBridge.test.js src/test/EditorAiWorkbench.test.js src/test/EditorShell.test.js --reporter=verbose
```

Result:

- Test files: 3 passed
- Tests: 33 passed
- Failures: 0
- Skips: 0

## Fix Applied During Validation

The first full Phase 15 regression run exposed two failing `EditorShell.test.js` assertions around heartbeat continuation while runtime-events SSE is healthy.

Root cause: `startRuntimeEventStreamForDocument` stopped both save-status polling and session heartbeat polling after the SSE stream became healthy, even though the intended contract is that SSE replaces save-status polling only. Editing-session heartbeat must continue independently so the backend does not mark the editor session inactive.

Fix:

- `packages/web/src/components/editor/EditorShell.vue`
  - keeps `stopSaveStatusPolling()`
  - replaces `stopSessionHeartbeatPolling()` with `startSessionHeartbeatPolling()`
  - updates the surrounding comment to reflect the actual runtime contract

Commit:

- `4ac3a46 fix(15-04): keep heartbeat active with runtime stream`

## Manual Smoke Checklist

Status: pending human validation in a live ONLYOFFICE editor session.

| Link | Scenario | Status | Notes |
|------|----------|--------|-------|
| 1 | selection capture: button -> bridge -> snapshot display | pending | Requires live editor selection |
| 2 | writeback: insert at cursor | pending | Requires live PasteHtml into document |
| 3 | writeback: replace selection | pending | Verify TOCTOU warning is visible; ONLYOFFICE may fall back to cursor insertion if focus clears selection |
| 4 | outline refresh and jump to heading | pending | Requires document with headings |
| 5 | saveStatus tag in workbench toolbar | pending | Automated shell coverage confirms propagation; visual status sequence still needs smoke |
| 6 | replace mode disabled without selection | pending | Unit covered; visual copy still needs smoke |
| 7 | bridge-not-ready error path | pending | Unit covered; live retry behavior still needs smoke |

## Self-Check

PASSED for automated Phase 15 regression.

Human verification remains open for the browser/ONLYOFFICE behaviors that cannot be proven by unit tests alone.
