---
phase: 17-java-ai-chatgpt
plan: 04
subsystem: docs-verification-security
tags: [llm, variants, regenerate, sse, vue, spring-boot, security]
requires:
  - phase: 17-java-ai-chatgpt-01
    provides: document_llm_message_variant persistence and DTO variant contract
  - phase: 17-java-ai-chatgpt-02
    provides: backend regenerate variant lifecycle and active variant prompt history
  - phase: 17-java-ai-chatgpt-03
    provides: frontend active variant rendering, switching, regenerate, copy, and writeback paths
provides:
  - Phase 17 regenerate variant protocol documentation
  - Backend/frontend/full regression verification results
  - Sensitive info log negative assertion and release closeout notes
affects: [17-java-ai-chatgpt, llm-workbench, docs, verification, security]
tech-stack:
  added: []
  patterns:
    - Documentation as protocol contract for backend/frontend variant identity
    - Fail-fast targeted regression before root verify
    - Static negative assertion for sensitive info logs
key-files:
  created:
    - .planning/phases/17-java-ai-chatgpt/17-04-SUMMARY.md
  modified:
    - docs/llm-workbench-phase14.md
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
    - packages/web/src/test/EditorShell.test.js
key-decisions:
  - "Phase 17 文档明确 assistant message 是稳定轮次容器，document_llm_message_variant 承载具体回复版本。"
  - "最终验证采用目标后端测试、目标前端测试和根级 npm run verify 三层收口。"
  - "安全收口以 log.info 负断言确认新增 variant 日志不输出 prompt、assistant/reasoning 正文、密钥、Authorization 或 raw payload。"
patterns-established:
  - "后续联调应通过 requestId -> assistantMessageId -> variantId 回查 regenerate 尝试。"
  - "写回、复制、插入预览、reasoning 和 provider meta 展示默认读取 active variant。"
  - "in_progress active variant 禁用复制和写回，避免半截内容进入用户文档。"
requirements-completed: [PH17-01, PH17-02, PH17-03, PH17-04, PH17-05, PH17-06, PH17-07, PH17-08, PH17-09]
duration: 12min
completed: 2026-04-28
---

# Phase 17 Plan 04: 文档、回归验证和安全收口 Summary

**ChatGPT 式 regenerate variants 协议文档、全量回归验证和敏感 info 日志负断言收口。**

## Performance

- **Duration:** 约 12 分钟
- **Started:** 2026-04-28T04:08:00Z
- **Completed:** 2026-04-28T04:20:31Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- 在 `docs/llm-workbench-phase14.md` 增补 Phase 17 extension，覆盖 variant 数据模型、SSE/REST 字段、active variant 切换、prompt history、UI 行为、失败/取消和日志安全规则。
- 目标后端测试、目标前端测试和根级 `npm run verify` 均通过。
- 完成 D-01 到 D-14 覆盖审计、review 共识风险检查、deferred ideas 范围检查和敏感 info 日志负断言。

## Task Commits

1. **Task 1: 更新 LLM 工作台协议文档** - `859cfc9` (docs)
2. **Task 2: 执行后端和前端回归验证** - `254e3fa` (test)
3. **Task 3: 执行源覆盖与敏感信息检查** - verification-only，结果记录在本 SUMMARY

## Files Created/Modified

- `docs/llm-workbench-phase14.md` - 新增 Phase 17 多版本 regenerate 协议和联调说明。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java` - 补齐 MVC slice 中 Phase 17 variant mapper mock，恢复全量后端回归。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java` - 补齐 MVC slice 中 Phase 17 variant mapper mock。
- `packages/web/src/test/EditorShell.test.js` - 将抽屉切换断言更新到当前 DOM selector。
- `.planning/phases/17-java-ai-chatgpt/17-04-SUMMARY.md` - 本计划执行总结。

## Decisions Made

- Phase 17 的文档契约以后端真实字段为准：`variantId`、`variantIndex`、`activeVariantIndex` 和 `variants[]` 是联调关键字段。
- request status 和 session detail 均作为断流/历史加载恢复依据；前端不需要额外轮询 variant 详情。
- info 日志只能保留稳定 ID、状态迁移和计数；DTO 可以出现字段名，但运行时日志不得输出敏感正文、密钥、Authorization 或原始 provider payload。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 恢复根级全量回归验证**
- **Found during:** Task 2
- **Issue:** 根级 `npm run verify` 会覆盖更宽的 MVC slice 和 EditorShell 测试；Phase 17 新增 variant mapper 后，MVC slice 需要补 mock，前端抽屉 selector 也需要对齐当前 DOM。
- **Fix:** 补齐 `DocumentLlmMessageVariantMapper` mock，并将 `EditorShell.test.js` selector 更新为 `.drawer-collapse-btn`。
- **Files modified:** `DocumentApiControllerTest.java`, `DocumentControllerTest.java`, `EditorShell.test.js`
- **Verification:** `npm run verify` 通过。
- **Committed in:** `254e3fa`

---

**Total deviations:** 1 auto-fixed (Rule 3)
**Impact on plan:** 仅修复阻塞全量验证的测试适配问题，没有改变应用运行逻辑或扩大 Phase 17 产品范围。

## Issues Encountered

- PowerShell 会把未加引号的 `-Dtest=ClassA,ClassB` 逗号解析为参数列表；目标 Maven 命令改为对 `-Dtest=...` 加引号后通过。
- `packages/server/onlyoffice-integration-service/src/main/resources/application.yml` 在执行前已有未提交修改；本计划未读取、未修改、未提交。
- 工作区存在未跟踪 `%SystemDrive%/` 目录；不属于本计划产物，按 ownership 保留未处理。
- Vitest 仍输出既有 Element Plus `[ElOnlyChild] no valid child node found` warning；测试断言全部通过。
- Maven 输出既有 repository id、Mockito dynamic agent 和 SpringDoc warning；未阻塞构建。

## Verification

- `Select-String -Path docs/llm-workbench-phase14.md -Pattern "Phase 17 extension|document_llm_message_variant|activeVariantIndex|variantId|失败|取消"` 通过，文档覆盖计划关键词。
- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=DocumentLlmMessageVariantRepositoryTest,LlmDtoContractTest,LlmConversationFlowTest,LlmConversationServiceTest" test` 通过：26 tests green。
- `pnpm --dir packages/web test -- src/test/EditorAiWorkbench.test.js --reporter=verbose` 通过：26 tests green。
- `npm run verify` 通过：后端 144 tests green，前端 56 tests green，web build 通过，`docker compose config` 通过。
- `git grep -n -E "log\\.info\\([^\\n]*(prompt|assistantText|reasoningContent|apiKey|Authorization|raw payload|rawPayload)" -- packages/server/onlyoffice-integration-service/src/main/java` 负断言通过。

## Coverage Audit

- D-01 到 D-04：文档记录 assistant message 稳定轮次容器、variants 数据模型、active index 和 variant 终态字段；后端 DTO/flow 测试覆盖。
- D-05 到 D-08：文档记录 request -> assistant message -> variant 审计链路、active prompt history、SSE/request/session variant 字段、首次生成与 regenerate 建单差异；后端 flow/service 测试覆盖。
- D-09 到 D-12：文档记录 active-only 展示、`‹ 2/3 ›` 切换、复制/写回读取 active variant、失败/取消不破坏 completed 版本；前端工作台测试覆盖。
- D-13 到 D-14：文档记录旧 assistant_text 兼容为 variant 0，并确认 Phase 16 reasoning/partial 语义保留；migration/flow/frontend 测试覆盖。
- Review 共识风险均有实现或测试点：variant index 并发分配、terminal/user active switch 竞态、prevActiveIndex 回滚、reconcile 精确到 variant、in_progress 复制/写回禁用。
- Deferred ideas 未进入 Phase 17 实现：未新增 variant diff、高亮、命名版本、删除版本、收藏版本或多分支对话树。

## Known Stubs

None - 本计划没有新增阻断目标的 stub、TODO、FIXME 或 placeholder。

## Threat Flags

None - 本计划新增的是文档和验证收口，没有新增网络端点、认证路径、文件访问模式或 schema trust boundary。Phase 17 既有 variant trust boundary 已在 17-01/17-02 summaries 记录。

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 17 已完成。后续 Phase 15/写回相关工作应继续消费 active variant 展开的 `assistantText`，并在需要更细粒度版本能力时另起范围，不把 diff、命名版本、删除版本或多分支对话树混入当前线性 variants 模型。

## Self-Check: PASSED

- 已确认 SUMMARY、`docs/llm-workbench-phase14.md` 存在。
- 已确认提交 `859cfc9`、`254e3fa`、`984fea8` 存在。
- 已确认目标测试、根级 verify 和敏感 info 日志负断言通过。

---
*Phase: 17-java-ai-chatgpt*
*Completed: 2026-04-28*
