---
phase: 08-cos
plan: 02
subsystem: service-contract-refactor-and-comment-hardening
tags: [service-contract, service-impl, comments, tests]
requires: [08-01]
provides:
  - Service 接口 + ServiceImpl 结构
  - 更完整的中文步骤注释
  - 面向契约的 controller / test 依赖边界
affects: [phase-03, phase-04, phase-05]
tech-stack:
  added: [service-impl-package, contract-oriented-tests]
  patterns: [interface-first-service, orchestration-comments, test-against-contract]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/OnlyofficeConfigServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStorageServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/OnlyofficeImageServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/RemoteResourceSecurityServiceImpl.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeImageService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/RemoteResourceSecurityService.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/*.java
key-decisions:
  - "保留原 service 名称给接口使用，默认实现统一迁移到 impl 包"
  - "controller 和其他上层调用继续面向接口注入，不让 impl 成为新的主依赖"
  - "关键中文注释优先解释步骤顺序、失败补偿和角色边界，而不是重复代码字面"
patterns-established:
  - "DocumentStorageServiceImpl 负责对象编排，DocumentMetadataServiceImpl 继续负责元数据真相源"
  - "OnlyofficeConfigServiceImpl、OnlyofficeImageServiceImpl 和 RemoteResourceSecurityServiceImpl 的职责边界已清晰分层"
requirements-completed: [SVC-01, DOC-01]
duration: 70min
completed: 2026-03-26
---

# Phase 8 / Plan 02 Summary

**service 包已经收敛为稳定契约层，关键业务编排也补上了系统化中文注释，后续维护不再需要反复逆向代码。**

## Accomplishments

- 把 `OnlyofficeConfigService`、`DocumentStorageService`、`OnlyofficeImageService`、`RemoteResourceSecurityService` 收敛成接口，并新增对应 `ServiceImpl` 默认实现。
- 之前已经接口化的 `AccessAuditService`、`DocumentMetadataService`、`DocumentStatusService`、`OnlyofficeJwtService` 与这轮新增实现一起形成统一结构。
- 单测里直接 new 具体类的地方已经切到 `*ServiceImpl`，但 mock 和业务依赖仍然保持接口类型。
- 在 `DocumentStorageServiceImpl`、`OnlyofficeConfigServiceImpl`、`RemoteResourceSecurityServiceImpl` 等关键实现里补充了中文步骤说明、失败补偿语义和角色边界注释。

## Execution Notes

- `DocumentNotFoundException` 保持为异常类，没有被错误抽象成契约层。
- `DocumentStorageServiceImpl` 明确承担对象编排职责，callback 回写、远程导入和 bootstrap 文档生成的步骤说明都已补齐。
- `RemoteResourceSecurityServiceImpl` 继续使用分块读取而不是一次性读全，中文注释里已经解释了这是为了让大小限制在读取过程中生效。

## Notes

- 这轮接口化没有改 controller 的主依赖名称，因此上层调用点改动很小，风险集中在实现迁移和测试构造器替换。
- Windows 下 apply_patch 的命令长度限制较严格，执行时按“小 patch、多次提交到工作区”的方式完成了重构。

---
*Phase: 08-cos*
*Completed: 2026-03-26*
