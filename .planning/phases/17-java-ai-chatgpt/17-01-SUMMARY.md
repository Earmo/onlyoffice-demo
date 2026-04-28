---
phase: 17-java-ai-chatgpt
plan: 01
subsystem: backend
tags: [spring-boot, mybatis-flex, flyway, llm, dto]
requires:
  - phase: 16-llm-streaming-and-deep-thinking-ui
    provides: reasoning SSE 与 providerResponseMeta.reasoningContent 终态契约
provides:
  - document_llm_message_variant 持久化表与历史 assistant message variant 0 回填
  - document_llm_message.active_variant_index 与 request variant 审计字段
  - scoped variant repository 与 DTO variant 契约
affects: [17-java-ai-chatgpt, llm-workbench, backend-api, database]
tech-stack:
  added: []
  patterns:
    - Flyway 幂等增量迁移
    - MyBatis-Flex scoped repository 查询
    - DTO 向后兼容构造器
key-files:
  created:
    - packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V10__add_llm_message_variants.sql
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentLlmMessageVariantEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/mapper/DocumentLlmMessageVariantMapper.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentLlmMessageVariantRepository.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/LlmMessageVariantResponse.java
  modified:
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentLlmMessageEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentLlmRequestEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentLlmMessageRepository.java
    - packages/server/onlyoffice-integration-data/src/test/java/com/earmo/onlyoffice/integration/data/repository/DocumentLlmMessageVariantRepositoryTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/LlmConversationFlowTest.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/LlmMessageResponse.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/LlmRequestStatusResponse.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/LlmStreamEventResponse.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/SendLlmMessageRequest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/LlmDtoContractTest.java
key-decisions:
  - "使用独立 document_llm_message_variant 表承载 assistant 版本，message 仅保存 active_variant_index。"
  - "DTO 新字段采用向后兼容构造器，避免破坏现有 controller/service 测试调用点。"
  - "Task 3 验证使用 data+service reactor，避免本机旧 data SNAPSHOT artifact 影响 service-only 编译。"
patterns-established:
  - "Variant repository 查询和更新必须带 documentId、tenantId、actorUser 作用域。"
  - "request/status/stream DTO 暴露 variant identity，但 providerResponseMeta 不新增敏感字段。"
requirements-completed: [PH17-01, PH17-03, PH17-06, PH17-09]
duration: 11min
completed: 2026-04-28
---

# Phase 17 Plan 01: 后端 Variant 持久化与 DTO 契约 Summary

**LLM assistant 回复版本表、历史 variant 0 回填、scoped repository 和 variant-aware DTO 契约基础**

## Performance

- **Duration:** 约 11 分钟
- **Started:** 2026-04-28T03:08:34Z
- **Completed:** 2026-04-28T03:19:25Z
- **Tasks:** 3
- **Files modified:** 15

## Accomplishments

- 新增 Flyway V10，创建 `document_llm_message_variant`，为 `document_llm_message` 增加 `active_variant_index`，为 `document_llm_request` 增加 `variant_id` / `variant_index`。
- 历史 assistant rows 回填为 `variant_index=0`；pending assistant variant 保持 `assistant_text=null`，不伪装成 completed 文本。
- 新增 variant entity、mapper、repository，所有读取路径带 `documentId + tenantId + actorUser` 作用域，并为 next variant 创建提供唯一索引冲突重试。
- 扩展 `LlmMessageResponse`、`LlmRequestStatusResponse`、`LlmStreamEventResponse` 和 `SendLlmMessageRequest` 的 variant 契约。

## Task Commits

1. **Task 1 RED: variant 持久化测试** - `c09ae6b` (test)
2. **Task 1/2 GREEN: variant 表、实体、仓储** - `0d1227f` (feat)
3. **Task 3 RED: DTO 契约测试** - `29d368c` (test)
4. **Task 3 GREEN: DTO variant 契约** - `91c74bf` (feat)

## Files Created/Modified

- `packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V10__add_llm_message_variants.sql` - variant 表、索引、request/message 字段和历史回填。
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentLlmMessageVariantEntity.java` - variant 持久化实体。
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentLlmMessageVariantRepository.java` - scoped variant 查询、更新和 next variant 创建。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/llm/LlmMessageVariantResponse.java` - 前端消费的 variant DTO。
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/LlmDtoContractTest.java` - JSON 契约覆盖 variants、variant identity 和 regenerate target。

## Decisions Made

- 使用唯一索引 `(message_id, variant_index)` 防止同一 assistant 轮次版本序号冲突。
- 保留 DTO 旧构造器，将新增字段设为 null/empty，降低本计划对 service/controller 调用点的改造面。
- `SendLlmMessageRequest` 只新增 `regenerateAssistantMessageId`，不接受 tenant/user 等浏览器不可控字段。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 避免 service-only Maven 命令使用旧 data SNAPSHOT**
- **Found during:** Task 3 DTO 验证
- **Issue:** `mvn -pl onlyoffice-integration-service -Dtest=LlmDtoContractTest test` 使用本机已安装旧 data artifact，缺少既有 `lastConversationTime` 字段，导致 service 编译失败。
- **Fix:** 使用 `-pl onlyoffice-integration-data,onlyoffice-integration-service` reactor 命令验证，让当前工作区 data 源码参与编译。
- **Files modified:** 无代码修改。
- **Verification:** `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=LlmDtoContractTest" test` 通过。
- **Committed in:** 不适用，验证流程调整。

---

**Total deviations:** 1 auto-fixed (Rule 3)
**Impact on plan:** 仅调整验证命令的 reactor 范围，未扩大代码实现范围。

## Issues Encountered

- 计划要求的 `DocumentLlmRequestRepository` 无需额外代码改动；现有 `update(entity)` 已可持久化新增 request variant 字段。
- 本次计划没有修改 `LlmConversationService`，因此 DTO 顶层字段仍由现有 assistant message 字段映射；真正写入/读取 active variant 的服务层语义留给 17-02。

## Known Stubs

None - 未发现阻断本计划目标的 stub、TODO、FIXME 或 placeholder。

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: database-surface | `packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V10__add_llm_message_variants.sql` | 新增 variant 表和 request variant 字段；已通过 scoped repository、唯一索引和 DTO meta 不扩展处理计划内威胁。 |

## Verification

- `mvn -f packages/server/pom.xml -pl onlyoffice-integration-data,onlyoffice-integration-service "-Dtest=DocumentLlmMessageVariantRepositoryTest,LlmDtoContractTest,LlmConversationFlowTest#flywayMigratesExistingAssistantRowsToVariantZero" test` 通过。

## User Setup Required

None - 无新增外部服务配置。

## Next Phase Readiness

17-02 可以在此基础上把首次生成和 regenerate 服务层语义接入 variant 表，填充 request 的 `variantId` / `variantIndex` 并让 session detail 读取 active variant。

## Self-Check: PASSED

- 已确认 SUMMARY、V10 migration、variant entity、variant DTO 文件存在。
- 已确认任务提交 `c09ae6b`、`0d1227f`、`29d368c`、`91c74bf` 存在。

---
*Phase: 17-java-ai-chatgpt*
*Completed: 2026-04-28*
