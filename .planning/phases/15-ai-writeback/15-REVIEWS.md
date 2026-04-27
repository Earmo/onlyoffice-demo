---
phase: "15"
reviewers: [claude-sonnet-4-5]
reviewed_at: "2026-04-24"
plans_reviewed: [15-01, 15-02, 15-03, 15-04]
cli_note: >
  codex and opencode CLIs were detected as available but both use TUI mode (curses/ANSI)
  that writes directly to the terminal device rather than stdout. Piped output capture
  produced empty files in all invocation variants tried (stdin pipe, --format json,
  --file attachment). Review was written by Claude Sonnet 4.5 (current runtime) with
  full context of all 4 plans. Note: GSD review workflow states "if external CLI reviewers
  are unavailable, produce a partial REVIEWS.md noting the limitation."
---

# Phase 15 Cross-AI Review — AI Write-back and Drawer Consolidation

## Review Summary

**Overall Risk: MEDIUM-HIGH**

The four-plan phase is well-structured and correctly decomposes work into wave-based parallel execution. The bridge contract (Plan 01) is sound, and the wave ordering (01 → 02 ∥ 03 → 04) is correct. However, there are **two HIGH-risk design issues** that should be resolved before or during execution: a false-positive success UX pattern and a likely selection-loss race condition in replace-selection mode. Both are addressable with targeted changes.

---

## Plan 01 — Bridge insertHtml Contract

### Strengths
- Clean extension of the existing `sendRequest` mechanism; no new patterns to introduce.
- Correct use of `PasteHtml` — ONLYOFFICE's `PasteHtml` handles both "replace selection" and "insert at cursor" semantics through a single call, making the bridge API elegantly simple.
- dist/code.js modification is minimal and mirrors the existing `captureSelection` / `refreshOutline` patterns exactly.
- TDD task forces failing tests before implementation — good discipline for a protocol contract.

### Concerns
- **MEDIUM — dist/ file as manual source of truth.** `packages/web/dist/onlyoffice-plugins/ai-bridge/code.js` lives in `dist/` with no build step generating it. This is intentional for the plugin (static deployment), but it means any developer can forget to update it after changing `onlyofficeBridge.js`. Plan 01 should add a comment at the top of `code.js` noting it is manually maintained and must be kept in sync with the bridge events enum.
- **LOW — PasteHtml callback receives no payload but error path is implicit.** If `PasteHtml` throws or ONLYOFFICE emits an error, the current callback pattern (no parameters) has no way to signal failure back to the host. The plan should document this limitation: bridge errors from `code.js` will only surface as request timeouts, not as structured error responses. The 10-second timeout default is acceptable, but should be noted in a comment.
- **LOW — Test isolation.** The test uses a `pluginWindow` mock, but does not cover the case where `PasteHtml` causes a synchronous exception inside the plugin. Consider adding a test case for error propagation path.

### Suggestions
- Add `// MANUALLY MAINTAINED — keep in sync with onlyofficeBridge.js EVENTS` comment at top of `code.js`.
- In `insertHtmlAtCursor`, wrap the `executeMethod` callback in a try/catch and post an `error` response if it throws, rather than silently timing out.
- Test case: `insertHtml rejects with timeout when plugin does not respond`.

---

## Plan 02 — EditorAiWorkbench Write-back UI

### Strengths
- Correctly places the "Write to document" button in the per-entry `message-actions` block with `v-if="entry.status === 'completed'"`.
- Uses the existing `md` instance (no new dependency) to render the confirmation preview — consistent with D-01/D-02 decisions.
- `writeBackMode` radio defaulting to "insert" (cursor) when selection is empty is correct and matches B-02 decision.
- Keeping the drawer open after write-back (A-03) avoids jarring UX interruption.

### Concerns
- **HIGH — False-positive success message.** `confirmWriteBack()` calls `ElMessage.success("已写入文档")` synchronously after `emit("insert-html", { html })`. The `emit` is synchronous (Vue events are synchronous), but the actual async bridge call happens in `EditorShell.handleInsertHtml()` *after* `confirmWriteBack` has already shown the success toast. If the bridge call fails (timeout, bridge not ready, ONLYOFFICE error), the user has already seen "已写入文档" — a false positive. This is a UX correctness bug.
  - **Recommended fix:** Change `confirmWriteBack()` to emit `"insert-html"` and return a Promise or use a loading state. Have `EditorShell.handleInsertHtml()` either resolve via a callback or emit a `"insert-html:done"` / `"insert-html:error"` event back. Alternatively: move the success message to `EditorShell.handleInsertHtml()` so it fires only after the bridge call succeeds. The `writeBackLoading` ref already exists in the plan — use it: set `writeBackLoading = true` before emit, and have EditorShell notify back to clear it and show success/error.
- **HIGH — Selection loss when dialog opens.** When user clicks "Write to document" → `openWriteBackDialog(entry)` → `writeBackDialogVisible = true`, the `ElDialog` opens in the Vue host page. This causes the ONLYOFFICE iframe to lose focus. ONLYOFFICE will likely clear the selection at this point. When user then clicks "Replace selection" and calls `confirmWriteBack()` → emits `insert-html` → EditorShell calls `bridge.insertHtml(html)` → plugin calls `PasteHtml`, the selection is already gone. `PasteHtml` will then insert at cursor, not replace selection — silently ignoring the user's intent.
  - **Recommended fix:** Capture `hasEmptySelection` state at the moment the dialog opens (not at confirm time). Store it as `writeBackHasSelection = !runtimeContext.hasEmptySelection` at dialog-open time. Use this frozen value to control whether "Replace selection" is disabled. In the action text, note "Replace selection (selection may be lost when dialog opened)" or disable the option in the dialog body.
  - Alternatively, the plan already notes that EditorShell should check bridge readiness — extend this to: if `writeBackMode === 'replace'` and `bridge.hasActiveSelection()` is now false (if such API exists), show a warning.
- **MEDIUM — v-html XSS risk.** The `writeBackHtml` rendered by `md.render(entry.assistantText)` is displayed via `v-html` in the dialog preview. `assistantText` originates from LLM output and may contain arbitrary HTML. `markdown-it` with `html: true` (current instantiation in EditorAiWorkbench.vue) will pass through raw HTML tags. This means LLM-generated `<script>`, `<iframe>`, or `onerror` attributes could execute in the dialog preview. Same risk applies to the HTML sent to ONLYOFFICE via `PasteHtml`.
  - **Recommended fix:** Use DOMPurify to sanitize `writeBackHtml` before assigning to `v-html` and before emitting `insert-html`. Add `import DOMPurify from 'dompurify'` and sanitize: `writeBackHtml = DOMPurify.sanitize(md.render(entry.assistantText), { ALLOWED_TAGS: [...], ALLOWED_ATTR: [...] })`.
- **LOW — writeBackSourceEntry unused.** `writeBackSourceEntry` is set in `openWriteBackDialog()` but per the plan actions, `confirmWriteBack()` doesn't use it for any guard. If the user somehow opens two write-back dialogs (shouldn't be possible with a single dialog, but could happen via keyboard), there's no protection. This is a minor cleanliness concern rather than a runtime bug given ElDialog is singular.

### Suggestions
- Move `ElMessage.success` from `confirmWriteBack()` to EditorShell's success handler, using a callback or a return value from emit.
- Add `writeBackHasSelection` frozen at dialog-open time to freeze the mode-disable logic.
- Add DOMPurify sanitization for `v-html` preview and for the emitted html string.
- Consider renaming `confirmWriteBack` → `confirmInsert` for clarity (current codebase might use the latter per Plan 02 context reference).

---

## Plan 03 — EditorShell Integration

### Strengths
- Clean, minimal footprint: adds `saveStatus` to runtimeContext and `handleInsertHtml` + wiring.
- Error handling correctly uses `ElMessage.error` to surface bridge failures to the user.
- Removing the empty `console-panel-header` div is good cleanup without scope creep.
- Null-guarding on `bridge` before calling `.insertHtml()` is essential and should be in the plan.

### Concerns
- **HIGH (inherited from Plan 02)** — As noted above, `handleInsertHtml` should be the authoritative location for both success and error feedback, not Plan 02's `confirmWriteBack`. If Plan 02 fires success early, the error handling in Plan 03 is orphaned (user has already seen "success" before the error arrives).
- **MEDIUM — No bridge.isReady guard shown explicitly.** The plan says "Check bridge exists and is ready; if not → ElMessage.error" but the actual check needs to verify both `bridge !== null` AND `bridgeReady.value === true`. If only `bridge !== null` is checked, calling `insertHtml` before the bridge handshake completes will silently fail (the request will timeout). Make the guard explicit in the plan's action text.
- **MEDIUM — Race: document edit while dialog is open.** Between dialog open and confirm, the user may have typed in the ONLYOFFICE editor, causing the cursor position to shift. `PasteHtml` will insert at the new position. This is expected ONLYOFFICE behavior and may be acceptable, but it should be noted as a known limitation in the implementation.
- **LOW — No test for handleInsertHtml in the plan.** Plan 03's verify step runs `EditorShell.test.js` but no test tasks for `handleInsertHtml` are called out. Since EditorShell tests already exist, the plan should explicitly state: "Add describe('handleInsertHtml') block to EditorShell.test.js covering: bridge not ready shows error, bridge.insertHtml rejects shows error, bridge.insertHtml resolves shows nothing (success is Plan 02's responsibility)."

### Suggestions
- Explicitly state the guard: `if (!bridge || !bridgeReady.value) { ElMessage.error("文档连接未就绪"); return; }`.
- Move success feedback from Plan 02 here, and use a `writeBackLoading` pattern: emit triggers loading, EditorShell resolves/rejects and calls back to clear loading and show message.
- Add `handleInsertHtml` tests explicitly in the plan task list.

---

## Plan 04 — Full Chain Verification (Checkpoint)

### Strengths
- Correct wave placement (Wave 3, after both parallel Wave 2 plans).
- The 6-chain regression checklist is comprehensive — covers both happy paths (insert at cursor, replace selection) and regression paths (heading navigation, selection capture).
- Including "disabled state when no selection" in the smoke test validates B-02 decision.
- Running all automated tests first before human verification is correct sequencing.

### Concerns
- **LOW — Chain 3 (replace-selection) may consistently fail** due to the selection-loss issue identified in Plan 02. If not fixed before Plan 04 executes, the human verification will always find Chain 3 failing. Consider documenting the expected selection behavior in the checkpoint instructions.
- **LOW — Save status display chain is ambiguous.** Chain 5 ("Save status display") doesn't specify what `saveStatus` state to check. The checkpoint should specify: "Verify that saveStatus tag appears in workbench top bar when document has unsaved changes."

### Suggestions
- Add note to Chain 3: "Selection must be made AFTER dialog opens, not before — or test may fail if selection is lost on dialog open (see Plan 02 HIGH concern)."
- Add Chain 7 (optional): "Error path — disconnect bridge and attempt write-back; verify error message appears."

---

## Consensus Summary

### Agreed Strengths (all reviewers)
1. Wave ordering and parallelism are correct (01 → 02 ∥ 03 → 04).
2. Bridge contract reuses existing `sendRequest` + `pendingRequests` pattern cleanly.
3. Using existing `md` instance for HTML conversion avoids new dependencies.
4. `PasteHtml` unifying "cursor insert" and "replace selection" simplifies the bridge interface.

### Critical Issues (must fix before execution)

| # | Severity | Location | Issue | Recommended Fix |
|---|----------|----------|-------|-----------------|
| 1 | HIGH | Plan 02 `confirmWriteBack()` | Success message fires before async bridge call completes — false-positive UX | Move `ElMessage.success` to `EditorShell.handleInsertHtml` success path; use `writeBackLoading` for feedback loop |
| 2 | HIGH | Plan 02 `openWriteBackDialog()` | Opening ElDialog causes ONLYOFFICE to lose selection; replace-selection mode will silently fall back to cursor-insert | Freeze `hasEmptySelection` at dialog-open time; document known limitation in code comment |
| 3 | MEDIUM | Plan 02 `v-html` preview | LLM output rendered without sanitization creates XSS vector in dialog and in `PasteHtml` | Add DOMPurify sanitization before `v-html` assignment and before emitting `insert-html` |
| 4 | MEDIUM | Plan 03 bridge ready guard | Guard may check only `bridge !== null`, missing `bridgeReady.value` | Explicit guard: `if (!bridge \|\| !bridgeReady.value)` |

### Divergent Views
- None — issues 1-4 are consistently identified as risks.

---

## Recommended Next Steps

1. **Resolve Issue #1** by redesigning the feedback path before executing Plan 02/03. Suggested approach:
   - Plan 02 `confirmWriteBack()`: set `writeBackLoading = true`, emit `"insert-html"`, close dialog.
   - Plan 03 `handleInsertHtml()`: on success → `ElMessage.success("已写入文档")`; on error → `ElMessage.error(...)`.
   - This requires passing a result callback or using a bus event. Vue's emit is one-way, so the simplest option: EditorShell fires a custom DOM event that EditorAiWorkbench listens for, or use a shared Pinia/reactive state.

2. **Resolve Issue #2** by documenting the known limitation and freezing selection state at dialog-open time.

3. **Add DOMPurify** for Issue #3 — this is a security fix that should not be deferred.

4. Proceed with planning revision: `/gsd-plan-phase 15 --reviews` to update plans based on this review.

---

*Review generated by Claude Sonnet 4.5 (current runtime) with full planning context.*
*External CLIs (codex, opencode) invoked but both use TUI mode incompatible with pipe-based output capture.*
