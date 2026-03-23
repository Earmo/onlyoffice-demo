---
phase: 03-user-context-integration
plan: 03
subsystem: audit-and-extension
tags: [audit, callback, system-event, extension, provider-override]
requires:
  - phase: 03-01
    provides: AccessContext SPI 与可配置 provider 链
  - phase: 03-02
    provides: editor-config 与文档 API 已消费访问上下文
provides:
  - 轻量访问审计事件表与记录服务
  - callback 的 system event 审计语义
  - 自定义 AccessContextProvider 的扩展约定与接入文档
affects: [phase-05, phase-06]
tech-stack:
  added: [access-audit-event, access-audit-service]
  patterns: [lightweight-audit-log, truthful-system-callback, provider-override-extension]
key-files:
  created:
    - packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V3__create_access_audit_event.sql
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/AccessAuditEventEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/AccessAuditEventRepository.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/AccessAuditService.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
    - packages/server/onlyoffice-integration-service/src/main/resources/application.yml
    - docs/minimal-integration.md
key-decisions:
  - "callback 审计记录为 system event，不伪装为某个人类用户的直接保存动作"
  - "Phase 3 只补轻量审计事件，不扩成完整审计中心"
  - "外部系统接自己的用户中心时，只需要新增 provider 和配置"
patterns-established:
  - "审计落库：关键用户路径和系统 callback 都有轻量事件记录"
  - "扩展覆盖：自定义 provider 可通过配置参与并覆盖内置解析顺序"
requirements-completed: [USER-02, USER-03]
duration: 35min
completed: 2026-03-23
---

# Phase 3 / Plan 03 Summary

**Phase 3 的收口层已经补齐：轻量访问审计可以记录关键用户路径和 callback，starter 也已经明确支持外部系统覆盖访问上下文 provider。**

## Accomplishments

- data 模块新增 `access_audit_event` 表、实体、mapper、repository，支持按文档或租户查询审计事件。
- service 模块新增 `AccessAuditService`，已经覆盖 `document_created`、`document_uploaded`、`document_imported`、`editor_config_requested`、`callback_received` 等关键事件。
- `DocumentApiController` 与 `DocumentController` 已在创建、上传、导入、editor-config、callback 等路径上接通轻量审计。
- callback 现在固定以 `system` 事件源落库，不再伪装成“某个用户点了保存”。
- `docs/minimal-integration.md` 和 `application.yml` 注释都已补充 provider 覆盖、自定义扩展、错误语义和最小 permissions map 的接入说明。

## Execution Commits

- **实现提交：** `361fcd5` `feat(phase3): 落地用户上下文接入与轻量审计`

## Notes

- 轻量审计表当前只承载关键操作事件，后续如果要补更完整的版本差异或历史回放，建议放到后续 phase 扩展。
- WebMvc 测试里已补上 `AccessAuditService` 和 mapper mock，避免 starter 级装配把新审计依赖漏掉。

---
*Phase: 03-user-context-integration*
*Completed: 2026-03-23*
