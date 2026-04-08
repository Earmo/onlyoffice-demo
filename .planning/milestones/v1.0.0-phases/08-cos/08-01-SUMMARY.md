---
phase: 08-cos
plan: 01
subsystem: runtime-profile-split-and-build-coordinate-alignment
tags: [profiles, config, maven, docs, windows-debug]
requires: []
provides:
  - dev/test/prod 正式 profile 模型
  - Windows 调试覆盖层与正式 profile 的清晰边界
  - starter/service Maven 坐标语义收敛
affects: [phase-06]
tech-stack:
  added: [application-dev.yml, application-test.yml, application-prod.yml]
  patterns: [shared-base-config, profile-overlay, starter-parent-alignment]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/resources/application-dev.yml
    - packages/server/onlyoffice-integration-service/src/main/resources/application-test.yml
    - packages/server/onlyoffice-integration-service/src/main/resources/application-prod.yml
  modified:
    - packages/server/pom.xml
    - packages/server/onlyoffice-integration-service/pom.xml
    - packages/server/onlyoffice-integration-service/src/main/resources/application.yml
    - packages/server/onlyoffice-integration-service/src/main/resources/application-windows-debug.yml
    - packages/server/onlyoffice-integration-service/src/test/resources/application.yml
    - README.md
    - docs/configuration-matrix.md
    - docs/standalone-deployment.md
key-decisions:
  - "application.yml 只保留共享配置与 profile 入口，环境差异下沉到 dev/test/prod"
  - "windows-debug 保持为 dev 的覆盖层，而不是第四种正式部署环境"
  - "父工程 artifactId 收敛为 onlyoffice-integration-starter，服务模块收敛为 onlyoffice-integration-service"
patterns-established:
  - "本地开发、测试、生产和 Windows 断点调试现在都具备清晰的 YAML 入口"
  - "profile 选择、坐标命名和部署文档已同步收口，避免运行语义与文档脱节"
requirements-completed: [CFG-01, MOD-02]
duration: 55min
completed: 2026-03-26
---

# Phase 8 / Plan 01 Summary

**后端运行配置已经从“单一大配置 + 零散覆盖”升级为正式 profile 模型，starter 与 service 的构建坐标语义也已经收敛。**

## Accomplishments

- 新增 `application-dev.yml`、`application-test.yml`、`application-prod.yml`，把开发、测试、生产差异从共享配置中拆开。
- `application.yml` 现在显式负责 `SPRING_PROFILES_ACTIVE` 入口与共享配置，`windows-debug` 通过 profile group 叠加在 `dev` 上。
- 父工程 `packages/server/pom.xml` 已正式使用 `onlyoffice-integration-starter` 作为聚合坐标，服务模块则使用 `onlyoffice-integration-service`。
- Dockerfile、README、配置矩阵和独立部署文档都已经同步使用新的坐标和 profile 语义。

## Execution Notes

- 开发环境保留 `local` 作为默认 provider，降低本地启动门槛。
- 生产环境继续维持“显式配置优先”，关键地址和对象存储凭证缺失时应尽早失败。
- `windows-debug` 仍然只承担 Windows + Docker Desktop 下的本地断点调试覆盖职责，没有和正式环境 profile 混淆。

## Notes

- `src/test/resources/application.yml` 已显式激活 `test` profile，自动化测试不会再意外继承开发环境默认值。
- 这一步为后续 COS 和 service 接口化提供了稳定配置基础，不需要再回头补 profile 边界。

---
*Phase: 08-cos*
*Completed: 2026-03-26*
