---
phase: 06-verification-and-delivery
plan: 01
subsystem: backend-regression-hardening
tags: [backend-tests, mockmvc, service-tests, provider-routing, security]
requires: []
provides:
  - 后端高风险链路自动化回归网
  - provider 路由与默认策略测试骨架
  - 分布式编辑与安全语义的稳定回归保护
affects: [phase-05, phase-07]
tech-stack:
  added: [mockmvc-regression-tests, service-regression-tests, provider-routing-regressions]
  patterns: [high-risk-regression-first, service-plus-controller-coverage, provider-routing-baseline]
key-files:
  created: []
  modified:
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStatusServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStorageServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/OnlyofficeImageServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/StorageProviderResolverTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
key-decisions:
  - "后端测试优先覆盖 callback、安全边界、共享状态和 provider 路由这些高风险链路"
  - "repository 层保持必要覆盖，回归主入口继续围绕 service 与 MockMvc"
patterns-established:
  - "callback 拒绝、运行事件、地址 fail-fast 和远程资源拒绝都拥有明确自动化断言"
  - "provider 路由测试已能承接 local/minio 基线，并为后续 COS/OSS 保留同形扩展路径"
requirements-completed: [QUAL-01]
duration: 45min
completed: 2026-03-26
---

# Phase 6 / Plan 01 Summary

**后端高风险链路已经形成稳定回归网，Phase 5 的分布式与安全语义不再只靠人工记忆验证。**

## Accomplishments

- `DocumentStatusServiceTest` 新增 callback 拒绝事件断言，确保拒绝链路会写入独立运行事件，而不是误写保存失败状态。
- `OnlyofficeConfigServiceTest` 新增运行时 URL fail-fast 回归，配置成非 `http/https` 时会明确抛错。
- `DocumentStorageServiceTest` 与 `OnlyofficeImageServiceTest` 新增超大响应拒绝测试，覆盖远程导入和图片代理的大小上限语义。
- `StorageProviderResolverTest` 新增缺省上下文回退默认 provider 的回归断言，稳定了 provider 路由骨架。
- `DocumentControllerTest` 新增 editor-config 配置错误的显式错误语义断言，避免运行时坏配置被静默吞掉。

## Execution Notes

- 分组 Maven 回归命令已通过，后端新增测试与现有 data/service 模块测试可以并存。
- 根级 `npm run verify` 已再次跑通完整后端测试，当前 Maven 全量测试共 57 个测试全部通过。

## Notes

- Maven 仍会打印本机 `settings.xml` 的 repository id 警告，这不是 Phase 6 引入的问题。
- Mockito 在 JDK 21 下仍会输出动态 agent 警告，但不影响当前验证结论。

---
*Phase: 06-verification-and-delivery*
*Completed: 2026-03-26*
