---
phase: 07-module-boundaries-and-repository-refactor
plan: 01
subsystem: build-and-modules
tags: [maven, multi-module, spring-boot, starter, docker]
requires: []
provides:
  - 后端父聚合 POM 与 data/service 双模块结构
  - 以 service 模块为唯一运行入口的打包边界
  - 可继续向 starter 形态演进的模块目录基础
affects: [07-02, 07-03]
tech-stack:
  added: [maven-reactor, onlyoffice-integration-data, onlyoffice-integration-service]
  patterns: [aggregator-parent-pom, service-depends-on-data]
key-files:
  created:
    - packages/server/onlyoffice-integration-data/pom.xml
    - packages/server/onlyoffice-integration-service/pom.xml
  modified:
    - packages/server/pom.xml
    - packages/server/Dockerfile
    - docker-compose.yml
key-decisions:
  - "根 POM 改为聚合工程，data 与 service 以反应堆方式统一构建"
  - "可执行 jar 只由 service 模块产出，data 模块不再承担启动职责"
patterns-established:
  - "模块边界：data 承载持久化，service 承载 Web/API/运行时配置"
  - "统一构建入口：继续支持 `cd packages/server && mvn test/package`"
requirements-completed: [ARCH-04]
duration: 45min
completed: 2026-03-23
---

# Phase 7 / Plan 01 Summary

**后端已经从单模块工程切成父聚合 + data/service 双模块，service 模块成为唯一 starter 运行入口。**

## Accomplishments

- 将 `packages/server/pom.xml` 改成聚合父 POM，统一维护 Spring Boot、MyBatis-Flex、Knife4j 和 JWT 版本。
- 新建 `onlyoffice-integration-data` 与 `onlyoffice-integration-service` 两个子模块，明确数据库能力与运行时能力的边界。
- 更新 `Dockerfile` 与 compose 构建入口，让最终 jar 指向 `onlyoffice-integration-starter`。

## Execution Commits

- **实现提交：** `c30a6a2` `feat(phase7): 完成多模块拆分与starter重构`

## Notes

- 目录迁移时保留了原有 git 历史，避免整批文件“删除再新建”导致追踪断层。
- 模块拆分完成后，后续 repository 和字段命名重构都落在新模块中继续演进。

---
*Phase: 07-module-boundaries-and-repository-refactor*
*Completed: 2026-03-23*
