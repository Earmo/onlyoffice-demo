---
phase: 15-ai-writeback
plan: 01
subsystem: editor-bridge
tags: [onlyoffice, postmessage, writeback, vitest]

requires:
  - phase: 14-llm-conversation-chain
    provides: assistantText/assistantMessageId/sessionId writeback inputs
provides:
  - ONLYOFFICE bridge insertHtml/htmlInserted message contract
  - PasteHtml plugin handler for writing HTML into the document cursor or selection
  - insertHtml bridge tests covering resolve, error, and timeout paths
affects: [15-ai-writeback, editor-ai-workbench, onlyoffice-plugin]

tech-stack:
  added: []
  patterns:
    - postMessage requestId request/response matching
    - ONLYOFFICE PasteHtml best-effort callback handling

key-files:
  created:
    - packages/web/dist/onlyoffice-plugins/ai-bridge/code.js
  modified:
    - packages/web/src/components/editor/onlyofficeBridge.js
    - packages/web/src/test/onlyofficeBridge.test.js

key-decisions:
  - "insertHtml uses the existing sendRequest pendingRequests mechanism and resolves from htmlInserted payloads."
  - "PasteHtml callback is documented as best-effort because ONLYOFFICE does not provide success/error callback parameters."

patterns-established:
  - "Host bridge methods wait for ready, then send typed postMessage requests with requestId."
  - "Plugin synchronous execution failures are returned as bridge error events via postError."

requirements-completed: [WRIT-01]

duration: 5 min
completed: 2026-04-27
---

# Phase 15 Plan 01: InsertHtml Bridge Summary

**ONLYOFFICE writeback bridge with insertHtml/htmlInserted events and a PasteHtml plugin handler**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-27T10:53:30+08:00
- **Completed:** 2026-04-27T10:58:13+08:00
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Added `ONLYOFFICE_AI_BRIDGE_EVENTS.insertHtml = "onlyoffice-ai-bridge:insert-html"` and `htmlInserted = "onlyoffice-ai-bridge:html-inserted"`.
- Added `bridge.insertHtml(html): Promise<object>` using the existing `sendRequest` and `pendingRequests` requestId flow.
- Added the manually maintained plugin script with `insertHtmlAtCursor`, `PasteHtml`, `htmlInserted`, and synchronous error return via `postError`.
- Added tests for insertHtml success, plugin error rejection, and request timeout rejection.

## Task Commits

1. **Task 2 RED: insertHtml bridge tests** - `3a10232` (`test`)
2. **Task 1 GREEN: insertHtml bridge implementation** - `87b4358` (`feat`)

_Note: TDD was executed as a plan-level RED/GREEN flow because Task 1 required implementation while Task 2 supplied the failing bridge tests._

## Files Created/Modified

- `packages/web/src/components/editor/onlyofficeBridge.js` - Adds insertHtml/htmlInserted event constants and the host `insertHtml(html)` method.
- `packages/web/dist/onlyoffice-plugins/ai-bridge/code.js` - Adds the `MANUALLY MAINTAINED` header and `PasteHtml` writeback handling.
- `packages/web/src/test/onlyofficeBridge.test.js` - Covers resolve, error, and timeout insertHtml request paths.

## Decisions Made

- Reused `sendRequest` without a separate pending map for writeback because the existing requestId matching already handles resolve, reject, and timeout.
- Used plugin-side `postError` for synchronous `PasteHtml` exceptions so the host receives the same top-level `message` shape as existing bridge errors.
- Created the plan-specified `dist` plugin script from the existing public plugin baseline because the requested path did not exist in the repository checkout.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created missing plan-specified dist plugin script**

- **Found during:** Task 1
- **Issue:** `packages/web/dist/onlyoffice-plugins/ai-bridge/code.js` did not exist, but the plan required modifications and acceptance checks against that file.
- **Fix:** Created the file from the current `public/onlyoffice-plugins/ai-bridge/code.js` baseline, then added the manual-maintenance header and insertHtml/PasteHtml handling.
- **Files modified:** `packages/web/dist/onlyoffice-plugins/ai-bridge/code.js`
- **Verification:** `rg` confirmed `MANUALLY MAINTAINED`, `insertHtml`, `htmlInserted`, `PasteHtml`, `best-effort`, and `case EVENTS.insertHtml`.
- **Committed in:** `87b4358`

---

**Total deviations:** 1 auto-fixed (Rule 3 blocking)
**Impact on plan:** Required to satisfy the explicit output artifact path; no unrelated source files were modified.

## Verification

- PASS: `corepack pnpm --dir packages/web exec vitest run src/test/onlyofficeBridge.test.js --reporter=verbose`
  - `src/test/onlyofficeBridge.test.js`: 4 tests passed, including insertHtml resolve, error, and timeout.
- PARTIAL: `corepack pnpm --dir packages/web test -- src/test/onlyofficeBridge.test.js --reporter=verbose`
  - The plan target test file passed: `onlyofficeBridge.test.js` 4/4.
  - The Windows package script forwarded arguments in a way that also ran the broader suite; unrelated failures remained in `EditorShell.test.js` and `EditorAiWorkbench.test.js`.

## Issues Encountered

- The plan's exact `pnpm test -- ...` command did not isolate the requested test file under the current Windows script wrapper. The scoped `pnpm exec vitest run ...` command verified the intended test file successfully.
- Existing non-plan failures observed but not fixed due scope boundary:
  - `src/test/EditorShell.test.js`: two heartbeat/SSE expectations reported `expected 0 to be greater than 0`.
  - `src/test/EditorAiWorkbench.test.js`: one streaming display expectation did not find `思考方式`.

## Known Stubs

None. Stub scan found only legitimate runtime initializers and local aggregation variables, not UI-facing placeholder data.

## Threat Flags

None. The new host-to-plugin `insertHtml`/`PasteHtml` surface is the expected threat surface already covered by the plan threat model.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 02/03 can call `bridge.insertHtml(html)` and handle success, plugin error, or timeout through the established bridge Promise contract.

## Self-Check: PASSED

- FOUND: `packages/web/src/components/editor/onlyofficeBridge.js`
- FOUND: `packages/web/dist/onlyoffice-plugins/ai-bridge/code.js`
- FOUND: `packages/web/src/test/onlyofficeBridge.test.js`
- FOUND: `.planning/phases/15-ai-writeback/15-01-SUMMARY.md`
- FOUND commit: `3a10232`
- FOUND commit: `87b4358`

---
*Phase: 15-ai-writeback*
*Completed: 2026-04-27*
