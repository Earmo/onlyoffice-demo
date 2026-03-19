---
phase: 01-service-foundation
plan: 01
subsystem: infra
tags: [spring-boot, onlyoffice, docker-compose, nginx, runtime-config]
requires: []
provides:
  - 显式的 public/internal/document server 地址配置模型
  - 由后端统一生成的 ONLYOFFICE 绝对地址
  - 可作为聚合入口的 official web compose 部署语义
affects: [01-02, 01-03, phase-02]
tech-stack:
  added: [postgres-runtime-env, h2-file-default]
  patterns: [dual-base-url-config, backend-owned-editor-url]
key-files:
  created:
    - packages/server/src/test/java/com/earmo/onlyoffice/demo/config/DemoPropertiesTest.java
  modified:
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/config/DemoProperties.java
    - packages/server/src/main/resources/application.yml
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeConfigService.java
    - docker-compose.yml
    - packages/web/nginx.conf
key-decisions:
  - "保留 demo 聚合同域入口，但把它降级为官方前端部署形态之一"
  - "editor-config、file、callback 的绝对地址全部由后端统一生成"
patterns-established:
  - "运行时地址模式：publicBaseUrl、internalBaseUrl、documentServerUrl 三者职责分离"
  - "配置回退模式：请求上下文可推导的地址只作为兜底，不作为主配置来源"
requirements-completed: [ARCH-01, ARCH-03]
duration: 35min
completed: 2026-03-19
---

# Phase 1 / Plan 01 Summary

**服务配置已从 demo 风格升级为可分离部署的地址模型，ONLYOFFICE 编辑绝对地址统一由后端生成。**

## Performance

- **Duration:** 35 min
- **Started:** 2026-03-19T08:40:00Z
- **Completed:** 2026-03-19T09:15:00Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments

- 将 `DemoProperties` 改造成显式运行时配置模型，补齐 public/internal/document-server/default user 等服务化配置。
- 调整 `OnlyofficeConfigService`，让浏览器和 ONLYOFFICE 所需地址全部由后端按统一语义生成。
- 更新 compose 与 nginx 说明，明确 official web 只是聚合入口，不再假设它是唯一部署方式。

## Execution Commits

- **实现提交：** `dcc899e` `feat(phase1): 落地服务基础与文档服务API`

说明：本计划与 01-02、01-03 的实现存在较强文件耦合，因此以一笔跨计划实现提交落地，后续用 summary 和 verification 回写执行边界。

## Files Created/Modified

- `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/DemoProperties.java` - 提供服务化地址与默认上下文字段绑定。
- `packages/server/src/main/resources/application.yml` - 声明新的 demo 运行时键与默认 datasource/flyway 配置。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeConfigService.java` - 统一生成 editor config 中的文档地址和 callback 地址。
- `docker-compose.yml` - 保留聚合 demo，同时引入数据库与新的环境变量命名。
- `packages/web/nginx.conf` - 明确官方前端聚合入口定位。

## Decisions Made

- 使用 `publicBaseUrl` 面向浏览器和外部跳转，使用 `internalBaseUrl` 面向 ONLYOFFICE 文件下载与 callback。
- `documentServerUrl` 独立于业务服务入口配置，避免把 ONLYOFFICE 服务地址硬绑到同域代理模式。

## Deviations from Plan

None - plan executed with the intended runtime model, but implementation was committed together with later plan work because the shared service classes changed in one refactor.

## Issues Encountered

- 地址语义重构会同时影响 JWT、图片代理和 editor config 生成链路，因此同步更新了 `OnlyofficeJwtService` 与 `OnlyofficeImageService` 的配置读取方式。

## User Setup Required

None - compose demo 仍可直接启动，新的服务化配置通过环境变量可选覆盖。

## Next Phase Readiness

- 共享元数据和对外 API 已经可以建立在这套地址模型之上。
- Phase 2 的存储策略抽象可以继续复用 `public/internal/document server` 的分离语义。

---
*Phase: 01-service-foundation*
*Completed: 2026-03-19*
