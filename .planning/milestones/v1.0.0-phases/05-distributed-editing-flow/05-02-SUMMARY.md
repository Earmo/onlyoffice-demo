---
phase: 05-distributed-editing-flow
plan: 02
subsystem: onlyoffice-runtime-protocol-hardening
tags: [onlyoffice, callback, jwt, editor-config, fail-fast, distributed]
requires:
  - phase: 05-01
    provides: 共享运行状态与 save-status 摘要视图
provides:
  - callback 正式 JWT 验签闭环
  - 按角色统一生成的 ONLYOFFICE 运行时 URL
  - 坏配置尽早失败的运行时约束
affects: [05-03, phase-06]
tech-stack:
  added: [callback-jwt-verification, callback-rejected-audit, role-based-runtime-urls]
  patterns: [jwt-first-callback-trust, backend-owned-runtime-urls, fail-fast-config-validation]
key-files:
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeJwtService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/AccessAuditService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/GlobalExceptionHandler.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/OnlyofficeJwtServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
key-decisions:
  - "callback 可信性以 JWT 验签为主，不把来源白名单作为本阶段硬门槛"
  - "document.url、callbackUrl 和 documentServerUrl 都由后端按角色统一生成"
  - "关键地址配置不成立时直接 fail-fast，而不是继续按 request 猜测"
patterns-established:
  - "ONLYOFFICE 官方前端继续只消费 documentId 加 editor-config，运行时 URL 推导完全收口到后端"
  - "callback 被拒绝时同时记录运行状态和访问审计，形成可追踪拒绝语义"
requirements-completed: [EDIT-02, SAFE-01]
duration: 40min
completed: 2026-03-25
---

# Phase 5 / Plan 02 Summary

**ONLYOFFICE 的 editor-config、文件下载和 callback 链路已经补齐正式可信边界，分布式配置不再依赖静默猜测。**

## Accomplishments

- `OnlyofficeJwtService` 已支持 callback token 验签，`DocumentController` 在接收 callback 时会先做 JWT 校验，失败返回明确 `4xx` 并记录拒绝事件。
- `AccessAuditService` 已补 callback rejected 记录，运行态和审计都能看到未授权 callback 的拒绝痕迹。
- `OnlyofficeConfigService` 已把 `document.url`、`callbackUrl`、`documentServerUrl` 的生成权完全收口到后端，并对 `publicBaseUrl`、`internalBaseUrl`、`documentServerUrl` 做显式校验。
- `GlobalExceptionHandler` 已补 `IllegalStateException` 处理，坏地址配置会尽早返回明确错误，而不是落成模糊的运行时失败。

## Execution Commits

- **实现提交：** `24225fa` `feat(phase5): 落地分布式编辑运行链路`

## Notes

- 这一轮没有把来源地址白名单做成硬门槛，应用层 JWT 仍然是 callback 的主可信边界。
- 地址模型已经进一步产品化，官方前端不再参与任何 ONLYOFFICE 运行时 URL 推导。

---
*Phase: 05-distributed-editing-flow*
*Completed: 2026-03-25*
