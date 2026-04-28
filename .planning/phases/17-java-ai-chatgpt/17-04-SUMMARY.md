---
phase: 17-java-ai-chatgpt
plan: 04
subsystem: documentation-verification-security
tags: [docs, regression, security, llm, variants]
requires:
  - phase: 17-java-ai-chatgpt-01
    provides: variant persistence and DTO contract
  - phase: 17-java-ai-chatgpt-02
    provides: backend regenerate, stream, active switch, and prompt history semantics
  - phase: 17-java-ai-chatgpt-03
    provides: frontend active variant UI and operation paths
provides:
  - Phase 17 multi-version regenerate protocol documentation
  - Backend, frontend, build, and compose regression verification
  - Source coverage and info-log sensitive-field closeout
affects: [17-java-ai-chatgpt, llm-workbench, docs, regression-suite]
tech-stack:
  added: []
  patterns:
    - Documentation contract closeout
    - Full regression gate before phase completion
    - Static info-log negative assertion
key-files:
  created:
    - .planning/phases/17-java-ai-chatgpt/17-04-SUMMARY.md
  modified:
    - docs/llm-workbench-phase14.md
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
    - packages/web/src/test/EditorShell.test.js
key-decisions:
  - "Phase 17 文档将 assistant message 定义为稳定轮次容器，variants 承载具体回复。"
  - "全量 verify 暴露的 Phase 17 mapper slice 测试缺口按回归阻塞修复处理。"
  - "rg 在当前环境被拒绝访问时，使用 git grep / Select-String 执行等价审计。"
requirements-completed: [PH17-01, PH17-02, PH17-03, PH17-04, PH17-05, PH17-06, PH17-07, PH17-08, PH17-09]
duration: 12min
completed: 2026-04-28
---

# Phase 17 Plan 04: documentation, regression verification, and security closeout Summary

**Phase 17 多版本 regenerate 协议已写入联调文档，并通过目标回归、全量 verify 和日志安全负断言收口。**

## Performance

- **Duration:** 约 12 分钟
- **Started:** 2026-04-28T04:07:00Z
- **Completed:** 2026-04-28T04:18:55Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- 在 `docs/llm-workbench-phase14.md` 新增 “Phase 17 extension: ChatGPT 式多版本 regenerate”，覆盖数据模型、request -> variant 审计链路、SSE/REST 字段、active variant 切换、prompt history、前端 `‹ 2/3 ›` 控件、复制/写回和失败取消语义。
- 文档明确 `log.info(...)` 只允许稳定 ID、状态和计数，不允许 prompt、assistant/reasoning 正文、密钥、Authorization 或 raw provider payload。
- 目标后端测试、目标前端测试、`EditorShell` 修复验证和根级 `npm run verify` 均已通过。
- 完成 D-01 到 D-14 覆盖审计、review 共识风险检查、deferred ideas 负断言和 info 日志敏感字段负断言。

## Task Commits

1. **Task 1: Phase 17 协议文档** - `859cfc9` (docs)
2. **Task 2: 回归验证与阻塞修复** - `254e3fa` (test)
3. **Task 3: 源覆盖与日志安全审计** - `984fea8` (chore)

## Files Created/Modified

- `docs/llm-workbench-phase14.md` - 新增 Phase 17 多版本 regenerate 协议和日志安全规则。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java` - MVC slice mock 新增 `DocumentLlmMessageVariantMapper`。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java` - MVC slice mock 新增 `DocumentLlmMessageVariantMapper`。
- `packages/web/src/test/EditorShell.test.js` - 将过期 `.stage-edge-toggle` 选择器同步为当前 `.drawer-collapse-btn`。

## Decisions Made

- Phase 17 文档以 `document_llm_message_variant` 为权威版本存储，以 `activeVariantIndex` 作为展示、写回、历史加载和 prompt history 的默认选择。
- `GET /api/llm/requests/{id}`、`GET /api/llm/sessions/{id}`、SSE 和 `PUT /api/llm/messages/{messageId}/active-variant` 在文档中统一使用 `variantId/variantIndex/activeVariantIndex` 契约。
- 全量回归中的 MVC slice 启动失败属于 Phase 17 新 mapper 引入后的测试配置缺口，按 Rule 3 修复。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] rg 在当前环境不可执行**
- **Found during:** Task 1 文档验证
- **Issue:** `rg.exe` 从 WindowsApps 路径启动失败，报“拒绝访问”。
- **Fix:** 使用 `Select-String` 执行等价关键字验证；Task 3 使用 `git grep` 执行等价负断言。
- **Files modified:** 无。
- **Commit:** 不适用。

**2. [Rule 3 - Blocking] PowerShell 未引用 `-Dtest` 逗号参数**
- **Found during:** Task 2 后端目标测试
- **Issue:** PowerShell 将 `-Dtest=ClassA,ClassB` 逗号解析为参数列表，Maven 未启动。
- **Fix:** 将 `"-Dtest=DocumentLlmMessageVariantRepositoryTest,LlmDtoContractTest,LlmConversationFlowTest,LlmConversationServiceTest"` 整体加引号后重跑。
- **Files modified:** 无。
- **Commit:** 不适用。

**3. [Rule 3 - Blocking] Phase 17 新 mapper 破坏旧 MVC slice 测试启动**
- **Found during:** `npm run verify`
- **Issue:** 全量 server 测试中 `DocumentApiControllerTest` / `DocumentControllerTest` 加载 `DocumentLlmMessageVariantMapper`，但 WebMvcTest slice 没有 MyBatis `sqlSessionFactory`。
- **Fix:** 在两个 slice 测试中按既有 mapper mock 模式补 `DocumentLlmMessageVariantMapper` mock。
- **Files modified:** `DocumentApiControllerTest.java`, `DocumentControllerTest.java`
- **Commit:** `254e3fa`

**4. [Rule 3 - Blocking] EditorShell 测试选择器与当前 DOM 漂移**
- **Found during:** `npm run verify`
- **Issue:** `EditorShell.test.js` 仍查找旧 `.stage-edge-toggle`，当前组件按钮类为 `.drawer-collapse-btn`。
- **Fix:** 测试选择器同步为 `.drawer-collapse-btn`，不改变组件行为。
- **Files modified:** `EditorShell.test.js`
- **Commit:** `254e3fa`

## Verification

- `Select-String -Path docs/llm-workbench-phase14.md -Pattern "Phase 17 extension|document_llm_message_variant|activeVariantIndex|variantId|失败|取消"` 通过。
- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=DocumentLlmMessageVariantRepositoryTest,LlmDtoContractTest,LlmConversationFlowTest,LlmConversationServiceTest" test` 通过：26 tests green。
- `pnpm --dir packages/web test -- src/test/EditorAiWorkbench.test.js --reporter=verbose` 通过：26 tests green。
- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=DocumentApiControllerTest,DocumentControllerTest" test` 通过：29 tests green。
- `pnpm --dir packages/web test -- src/test/EditorShell.test.js --reporter=verbose` 通过：15 tests green。
- `npm run verify` 通过：server 144 tests、web 56 tests、web build、`docker compose config` 均通过。
- `git grep -n -E "log\\.info\\([^\\n]*(prompt|assistantText|reasoningContent|apiKey|Authorization|raw payload|rawPayload)" -- packages/server/onlyoffice-integration-service/src/main/java` 无命中。
- deferred ideas 负断言无命中：未发现 variant diff、高亮差异、命名版本、删除版本、收藏版本或多分支对话树实现。

## Known Stubs

None - 本计划修改的文档和测试文件未引入阻断目标的 TODO/FIXME/placeholder 或未接数据源 UI。

## Threat Flags

None - 本计划未新增生产网络端点、认证路径、文件访问模式或数据库 trust boundary；仅补充文档和测试配置。

## Issues Encountered

- `packages/server/onlyoffice-integration-service/src/main/resources/application.yml` 在执行前后均有未提交修改；按 ownership 要求未读取、未修改、未提交。
- 工作区存在无关未跟踪 `%SystemDrive%/` 目录；未读取、未删除、未提交。
- 全量前端测试仍输出既有 Element Plus `[ElOnlyChild] no valid child node found` warning，但测试全部通过。

## User Setup Required

None - 无新增环境变量或外部服务配置。

## Next Phase Readiness

Phase 17 已完成。多版本 regenerate 的后端、前端、协议文档、回归验证和日志安全审计均已收口，可进入最终验收或后续 Phase 15/写回联动验证。

## Self-Check: PASSED

- 已确认 SUMMARY、文档和 Task 2 涉及的测试文件存在。
- 已确认任务提交 `859cfc9`、`254e3fa`、`984fea8` 存在。
- Post-commit deletion check: no tracked file deletions across plan commits.
