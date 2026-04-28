---
phase: 17-java-ai-chatgpt
plan: 03
subsystem: frontend-ui
tags: [vue, vitest, llm, sse, variants, writeback]
requires:
  - phase: 17-java-ai-chatgpt-01
    provides: assistant message variants DTO and request/status/stream variant identity
  - phase: 17-java-ai-chatgpt-02
    provides: regenerate backend semantics, active variant switch endpoint, and prompt history active projection
provides:
  - Frontend assistant entries normalized to variants plus activeVariantIndex
  - ChatGPT-style previous/next variant switcher with persisted active variant updates
  - Regenerate streams merged into the same assistant entry variant instead of appending vertical messages
  - Copy, reasoning, meta, and writeback paths routed through the active completed variant
affects: [17-java-ai-chatgpt, llm-workbench, frontend-chat-variants, ai-writeback]
tech-stack:
  added: []
  patterns:
    - Vue active variant helper as the single read path for assistant rendering/actions
    - Variant identity stale guard for SSE and request reconciliation
    - Vitest interaction coverage for regenerate, cancel rollback, reconcile, switch, copy, and writeback
key-files:
  created: []
  modified:
    - packages/web/src/components/editor/EditorAiWorkbench.vue
    - packages/web/src/components/editor/editorAiApi.js
    - packages/web/src/test/EditorAiWorkbench.test.js
key-decisions:
  - "llmMessageStream.js 不改事件分发，继续透明传递 JSON payload；variant 合并逻辑集中在 EditorAiWorkbench。"
  - "active variant 是展示、reasoning、meta、复制和写回的唯一读取入口；旧顶层 assistant 字段兼容规范化为 variant 0。"
  - "重新生成失败或取消后恢复发起前 activeVariantIndex，同时保留失败/取消 variant 状态供用户后续查看。"
patterns-established:
  - "前端 entry 必须先 normalizeAssistantVariants，再通过 activeVariant(entry) 读取 assistant 内容。"
  - "SSE/回查合并必须匹配 documentId、sessionId、requestId、assistantMessageId 和 variant identity。"
  - "active variant 处于 in_progress 时禁用复制和写回，避免半截内容进入剪贴板或文档。"
requirements-completed: [PH17-03, PH17-05, PH17-07, PH17-09]
duration: 12min
completed: 2026-04-28
---

# Phase 17 Plan 03: Frontend Variants Entry and Switcher Summary

**Vue AI 工作台现在以 active variant 驱动展示、重新生成、复制和写回，并提供 ChatGPT 式版本切换。**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-28T03:47:43Z
- **Completed:** 2026-04-28T03:59:01Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- 将 assistant entry 规范化为 `variants + activeVariantIndex`，历史 variants 只渲染 active 版本；旧顶层响应兼容为 variant 0。
- 重新生成携带 `regenerateAssistantMessageId`，流式 delta、reasoning、terminal 和断流回查都合并到同一 entry 的目标 variant。
- 多版本回复显示 `‹ 2/3 ›` 紧凑切换控件，并通过后端 active variant endpoint 持久化用户选择。
- 复制、reasoning、meta、写回预览全部读取 active variant；active variant 生成中时禁用复制和写回。

## Task Commits

1. **Task 1 RED: active variant history rendering coverage** - `5d855d4` (test)
2. **Task 1 GREEN: normalize assistant entries** - `3d55dae` (feat)
3. **Task 2 RED: regenerate same-entry coverage** - `2648f7a` (test)
4. **Task 2 GREEN: stream regenerate variants into same entry** - `67c65a5` (feat)
5. **Task 3 RED: variant switch operation coverage** - `8dcaa1f` (test)
6. **Task 3 GREEN: variant switcher and active operations** - `3304550` (feat)

## Files Created/Modified

- `packages/web/src/components/editor/EditorAiWorkbench.vue` - active variant helpers、same-entry regenerate stream 合并、版本切换控件、copy/writeback guard。
- `packages/web/src/components/editor/editorAiApi.js` - 新增 `setLlmActiveVariant`，调用 `PUT /api/llm/messages/{messageId}/active-variant`。
- `packages/web/src/test/EditorAiWorkbench.test.js` - 覆盖历史 variants、regenerate 同 entry、取消回滚、断流回查、版本切换、复制/写回和 in-progress 禁用。

## Decisions Made

- `llmMessageStream.js` 无需修改：现有 SSE 解析器已经把 payload 原样交给组件，variant 字段在组件层按 entry/variant identity 合并即可。
- assistant Markdown 渲染改为复用 DOMPurify 清洗路径，避免 active variant 正文作为不可信 LLM 输出直接进入 `v-html`。
- 版本切换采用乐观 UI 更新；后端 PUT 失败时恢复 previous active index 并提示错误。

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- 执行前 `packages/server/onlyoffice-integration-service/src/main/resources/application.yml` 已有未提交修改；按 ownership 要求未读取、未修改、未提交。
- Vitest 通过但仍输出既有 Element Plus `[ElOnlyChild] no valid child node found` 测试环境警告；不影响本计划断言。

## Known Stubs

None - stub scan only found expected empty-state initialization, test mocks, and the textarea placeholder; no unimplemented UI/data path blocks this plan goal.

## Verification

- `pnpm --dir packages/web test -- src/test/EditorAiWorkbench.test.js --reporter=verbose` passed: 26 tests green.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

17-04 can document the final frontend/backend variant protocol and run broader regression/security validation. Frontend now consumes backend variant identity and active switch semantics.

## Self-Check: PASSED

- 已确认 SUMMARY、EditorAiWorkbench、editorAiApi 和 EditorAiWorkbench 测试文件存在。
- 已确认任务提交 `5d855d4`、`3d55dae`、`2648f7a`、`67c65a5`、`8dcaa1f`、`3304550` 存在。
- Post-commit deletion check: no tracked file deletions across plan commits.

---
*Phase: 17-java-ai-chatgpt*
*Completed: 2026-04-28*
