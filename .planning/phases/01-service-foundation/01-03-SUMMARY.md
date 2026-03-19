---
phase: 01-service-foundation
plan: 03
subsystem: api
tags: [spring-web, rest-api, request-context, headless-first, integration-docs]
requires:
  - phase: 01-01
    provides: 分离部署地址模型和后端统一生成的 editor config
  - phase: 01-02
    provides: 文档元数据服务与共享状态模型
provides:
  - 文档主数据 API 与 ONLYOFFICE 运行时接口的语义拆分
  - 标准化用户上下文解析入口
  - headless-first 的最小接入文档
affects: [phase-03, phase-04, phase-05]
tech-stack:
  added: [request-context-resolver]
  patterns: [api-first-document-lifecycle, standardized-upstream-context]
key-files:
  created:
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentApiController.java
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/web/RequestContextResolver.java
    - packages/server/src/test/java/com/earmo/onlyoffice/demo/web/DocumentApiControllerTest.java
  modified:
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeConfigService.java
    - docs/minimal-integration.md
key-decisions:
  - "open 和 create/import 明确分离，不做隐式 auto-create"
  - "用户上下文默认来自上游透传，请求头解析集中在单一 resolver"
patterns-established:
  - "API 分层：DocumentApiController 负责主数据接口，DocumentController 保留 ONLYOFFICE 运行时入口"
  - "上下文集中回退：默认 demo 用户只在 resolver 中兜底，不散落到 service"
requirements-completed: [ARCH-01, ARCH-02]
duration: 40min
completed: 2026-03-19
---

# Phase 1 / Plan 03 Summary

**文档服务已经暴露可被外部系统接入的列表、详情、创建、上传、导入 API，并通过标准化请求上下文承接上游用户信息。**

## Performance

- **Duration:** 40 min
- **Started:** 2026-03-19T10:10:00Z
- **Completed:** 2026-03-19T10:50:00Z
- **Tasks:** 3
- **Files modified:** 13

## Accomplishments

- 新增 `/api/documents` 系列接口，明确 list/create/upload/import/detail 的服务端契约。
- 把 ONLYOFFICE 运行时接口与文档主数据 API 拆开，避免继续把 demo 编辑流程当成唯一边界。
- 增加 `RequestContextResolver` 和接入文档，让上游系统能透传 `tenantId`、`sourceSystem`、`externalUserId`、`displayName`。

## Execution Commits

- **实现提交：** `dcc899e` `feat(phase1): 落地服务基础与文档服务API`

## Files Created/Modified

- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentApiController.java` - 提供文档列表、详情、创建、上传、远程导入接口。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/RequestContextResolver.java` - 解析标准请求头并回退到配置默认值。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java` - 保留 ONLYOFFICE 运行时 file/callback/editor-config 接口。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/model/DocumentSummaryResponse.java` - 返回租户、owner、来源和状态等主数据字段。
- `docs/minimal-integration.md` - 记录 headless-first 的 create/import/open/editor-config 接入方式。

## Decisions Made

- `POST /api/documents` 只做显式创建，不承担“打开时自动建文档”的隐式副作用。
- 请求头命名在 v1 固定为 `X-Tenant-Id`、`X-Source-System`、`X-External-User-Id`、`X-User-Display-Name`，后续再考虑 token claim 适配。

## Deviations from Plan

None - the plan outcome matches the intended API boundary and integration contract.

## Issues Encountered

- `OnlyofficeConfigService` 需要同时消费请求上下文与新的地址模型，所以和 01-01 的配置改造发生了交叉，最终统一在同一实现提交中收敛。

## User Setup Required

None - 默认可用请求头透传接入，不要求此阶段引入额外认证中间件。

## Next Phase Readiness

- Phase 3 的用户上下文适配可以在现有 resolver 出口上继续扩展，不需要重做文档核心。
- Phase 4 的首页文档列表可以直接消费新增的 `/api/documents` 列表接口。

---
*Phase: 01-service-foundation*
*Completed: 2026-03-19*
