---
phase: 03-user-context-integration
plan: 01
subsystem: access-context-contract
tags: [access-context, spi, header, jwt, error-handling]
requires: []
provides:
  - 可插拔的访问上下文 SPI 与内置 provider
  - 可配置的解析顺序、默认补齐和严格错误语义
  - 面向外部用户体系接入的统一访问上下文入口
affects: [03-02, 03-03, phase-04, phase-05]
tech-stack:
  added: [access-context-provider, header-provider, jwt-provider, default-provider]
  patterns: [spi-first-context-resolution, configurable-provider-chain, explicit-4xx-errors]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/AccessContext.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/AccessContextProvider.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/AccessContextResolver.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/HeaderAccessContextProvider.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/JwtAccessContextProvider.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/context/DefaultAccessContextProvider.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/RequestContextResolver.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/GlobalExceptionHandler.java
    - packages/server/onlyoffice-integration-service/src/main/resources/application.yml
key-decisions:
  - "用户上下文解析以 SPI 为核心，header 与 jwt 只是内置 provider"
  - "完全缺失访问上下文时返回 4xx，部分缺失字段才允许受控补齐"
  - "provider 顺序由配置决定，自定义 provider 可以覆盖内置解析顺序"
patterns-established:
  - "SPI-first：controller 和业务链路以后只依赖统一 AccessContext 出口"
  - "严格错误语义：缺少上下文与解析失败不再落成 500 或静默兜底"
requirements-completed: [USER-01, USER-02]
duration: 35min
completed: 2026-03-23
---

# Phase 3 / Plan 01 Summary

**访问上下文已经从单一请求头解析器升级为可插拔 SPI 链，header、jwt、自定义 provider 都能在统一出口下参与解析。**

## Accomplishments

- 新增 `AccessContext`、`AccessContextProvider`、`AccessContextResolver` 和三种内置 provider，把用户上下文接入能力从固定实现改成可扩展契约。
- 扩展 `OnlyofficeIntegrationProperties` 与 `application.yml`，补齐 `enabled-providers`、`resolution-order`、`require-explicit-context`、`allow-default-context` 及 JWT claim 映射配置。
- `RequestContextResolver` 现在仅作为兼容包装层，真实解析工作统一委托给 `AccessContextResolver`。
- `GlobalExceptionHandler` 已新增访问上下文异常处理，完全缺失和解析失败都会明确返回 4xx。

## Execution Commits

- **实现提交：** `361fcd5` `feat(phase3): 落地用户上下文接入与轻量审计`

## Notes

- 这一步只建立访问上下文契约和错误语义，不在这里扩成完整权限平台。
- 默认用户补齐已经受 `profile + allow-default-context` 共同约束，不再是永久无条件 fallback。

---
*Phase: 03-user-context-integration*
*Completed: 2026-03-23*
