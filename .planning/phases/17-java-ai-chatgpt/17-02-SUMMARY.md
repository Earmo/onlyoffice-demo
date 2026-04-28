---
phase: 17-java-ai-chatgpt
plan: 02
subsystem: backend-api
tags: [java, spring-boot, llm, sse, onlyoffice, tdd]

requires:
  - phase: 17-java-ai-chatgpt-01
    provides: document_llm_message_variant persistence, variant DTO fields, regenerate request DTO
provides:
  - Regenerate creates assistant message variants instead of vertical messages
  - SSE/request/session responses expose variant identity
  - Active variant switching with user choice precedence over late completed terminals
  - Prompt history uses only active assistant variants
affects: [llm-workbench, java-ai-chatgpt, frontend-chat-variants]

tech-stack:
  added: []
  patterns:
    - TDD red/green commits for backend behavior
    - Variant-aware request lifecycle and terminal persistence
    - Active-variant prompt history projection

key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/SetLlmActiveVariantRequest.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationAccessGuard.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmPromptWindowBuilder.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/LlmController.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/LlmConversationFlowTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/LlmConversationServiceTest.java

key-decisions:
  - "Regenerate reuses the original user/assistant round and allocates a new variant under the assistant message."
  - "Completed variants auto-activate only when the user has not explicitly switched active variants after request start."
  - "Prompt history projects assistant messages through active variant text before sending context to the provider."
  - "Verification uses the data+service Maven reactor to avoid stale local data SNAPSHOT classes."

patterns-established:
  - "Variant lifecycle: pending variant at request start, terminal status/content on the variant, top-level message fields derived from active variant."
  - "Scoped active variant update: document, session, tenant, actor user, assistant message, and variant ownership are checked before persistence."
  - "History safety: non-active assistant variants remain visible in session detail but do not enter provider prompt context."

requirements-completed: [PH17-02, PH17-03, PH17-04, PH17-07, PH17-09]

duration: 19min
completed: 2026-04-28
---

# Phase 17 Plan 02: Backend Regenerate Variant Semantics Summary

**ChatGPT-style backend regenerate flow with stream/request/session variant identity and active-variant prompt history.**

## Performance

- **Duration:** 19 min
- **Started:** 2026-04-28T03:23:49Z
- **Completed:** 2026-04-28T03:41:58Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- 首次生成现在创建 user message、assistant message、variant 0 和 request；regenerate 只在同一 assistant message 下追加 variant，不新增纵向消息。
- SSE started/delta/reasoning/meta/completed/cancel/error、request lookup、session detail 都带 variant identity，并从 active variant 展开顶层 assistant 字段。
- 新增 active variant 切换端点，持久化 `activeVariantIndex`，并保证用户显式切换优先于晚到 completed terminal 自动切换。
- prompt history 只使用每个 assistant message 的 active variant 文本，非 active variants 只作为可切换历史返回。
- failed/cancelled regenerate 会保留新 variant 的 partial/status/error，但不破坏已有 completed active 版本。

## Task Commits

1. **Task 1 RED: regenerate 建单覆盖** - `297e22f` (test)
2. **Task 1 GREEN: regenerate variant creation** - `aff30e3` (feat)
3. **Task 2 RED: stream 与 active switch 覆盖** - `b2b699e` (test)
4. **Task 2 GREEN: variant stream terminal semantics** - `d3cd3d2` (feat)
5. **Task 3 RED: active variant prompt history contract** - `fcc2eff` (test)
6. **Task 3 GREEN: active variants for history** - `bd7f9a0` (feat)
7. **Rule 1 fix: cancelled partial meta preservation** - `7ff1511` (fix)

## Files Created/Modified

- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/SetLlmActiveVariantRequest.java` - active variant 切换请求体。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/LlmController.java` - 暴露 `PUT /api/llm/messages/{messageId}/active-variant`。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationAccessGuard.java` - regenerate assistant message 作用域校验。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationService.java` - variant-aware request lifecycle、terminal persistence、active switch、request/session mapping。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmPromptWindowBuilder.java` - active assistant variant prompt history projection。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/LlmConversationFlowTest.java` - regenerate、SSE variant、active switch、prompt history 集成回归。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/LlmConversationServiceTest.java` - active variant history 单元契约。

## Decisions Made

- regenerate 不复制用户消息，也不创建新的 assistant 纵向轮次；同一轮 AI 回复的多个结果统一建模为 assistant message 下的 variants。
- completed terminal 自动切 active 采用条件语义：如果用户在 request start 后显式切换过 active variant，则 terminal 只落库本 variant 内容，不覆盖用户选择。
- session detail 返回完整 variants，同时顶层 `assistantText/status/meta` 展开 active variant，避免前端重复推导。
- prompt builder 保持兼容 overload，并通过 active variant text map 覆盖 assistant 历史文本。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 调整 Maven 验证范围为 data+service reactor**
- **Found during:** Task 1 verification
- **Issue:** 计划中的 service-only Maven 命令会命中本地旧 data SNAPSHOT，出现 `NoSuchMethodError setLastConversationTime`，无法验证本计划改动。
- **Fix:** 所有自动验证改用 `-pl onlyoffice-integration-data,onlyoffice-integration-service`，保证 data 模块与 service 测试同 reactor 编译。
- **Files modified:** 无代码改动。
- **Verification:** Task 1、Task 2、Task 3 和计划级验证均通过 reactor 命令。
- **Committed in:** 不适用。

**2. [Rule 1 - Bug] 修复取消路径覆盖 partial reasoning meta 的竞态**
- **Found during:** 计划级验证
- **Issue:** 用户 cancel path 可能先以空 accumulator 写入 cancelled variant，与 stream accumulator 的取消收口竞态，导致 partial assistant text 或 reasoning meta 被覆盖。
- **Fix:** 当 request 仍存在活跃 execution 且 cancel path 没有 accumulator 时，跳过 stale variant/message 写入，由 stream 收口保留 partial 内容。
- **Files modified:** `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationService.java`
- **Verification:** `LlmConversationFlowTest#shouldPreservePartialReasoningAndAssistantTextWhenCancelledAfterChunks` 通过；计划级 `LlmConversationFlowTest,LlmConversationServiceTest` 通过。
- **Committed in:** `7ff1511`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** 都是完成计划验证和保持 Phase 16 partial/reasoning 语义所需的正确性修复，没有扩大业务范围。

## Issues Encountered

- `application.yml` 在执行前已有未提交修改；按 ownership 要求未读取、未修改、未提交。
- 计划级验证首次发现 cancelled partial meta 回归，已在 Rule 1 修复中处理。

## Verification

- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=LlmConversationFlowTest#regenerateCreatesVariantWithoutNewConversationEntry,LlmConversationFlowTest#regenerateRejectsAssistantFromAnotherSession,LlmConversationFlowTest#concurrentRegenerateCreatesDistinctVariantIndexes" test`
- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=LlmConversationFlowTest#streamEventsExposeVariantIdentity,LlmConversationFlowTest#cancelledRegenerateKeepsPreviousCompletedVariant,LlmConversationFlowTest#switchActiveVariantPersistsScopedIndex,LlmConversationFlowTest#terminalCompletedDoesNotOverrideUserActiveSwitch" test`
- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=LlmConversationServiceTest,LlmConversationFlowTest#promptHistoryUsesOnlyActiveVariant" test`
- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=LlmConversationFlowTest,LlmConversationServiceTest" test`

## Known Stubs

None - stub scan found only SLF4J `{}` log placeholders in modified files.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 后端 variant lifecycle、active switch、prompt history 语义可供前端 Plan 17 后续 UI 接入。
- 前端需要消费 `variantId`、`variantIndex`、`activeVariantIndex`，并调用 active variant switch API 来切换版本。

## Self-Check: PASSED

- Summary file exists: `.planning/phases/17-java-ai-chatgpt/17-02-SUMMARY.md`
- Task commits found: `297e22f`, `aff30e3`, `b2b699e`, `d3cd3d2`, `fcc2eff`, `bd7f9a0`, `7ff1511`
- Post-commit deletion check: no tracked file deletions across plan commits.

---
*Phase: 17-java-ai-chatgpt*
*Completed: 2026-04-28*
