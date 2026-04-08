---
phase: 8
slug: cos
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-26
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Config binding** | `OnlyofficeIntegrationPropertiesTest` + profile-specific resource loading |
| **Service quick run** | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=OnlyofficeIntegrationPropertiesTest,OnlyofficeConfigServiceTest,DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentStatusServiceTest,AccessAuditServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| **Storage quick run** | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=StorageProviderResolverTest,MinioDocumentStorageStrategyTest,CosDocumentStorageStrategyTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| **Packaging verification** | `cd packages/server && mvn -q -DskipITs package` |
| **Compose verification** | `docker compose config` |
| **Estimated runtime** | ~150-360 seconds |

---

## Sampling Rate

- **After every config/profile task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=OnlyofficeIntegrationPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test`
- **After every service-interface task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=OnlyofficeConfigServiceTest,DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentStatusServiceTest,AccessAuditServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- **After every COS/provider task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=StorageProviderResolverTest,MinioDocumentStorageStrategyTest,CosDocumentStorageStrategyTest -Dsurefire.failIfNoSpecifiedTests=false test`
- **After every plan wave:** Run `cd packages/server && mvn test`
- **After full phase execution:** Run `cd packages/server && mvn -q -DskipITs package` and `docker compose config`
- **Max feedback latency:** 360 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 8-01-01 | 01 | 1 | CFG-01 | config-binding | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=OnlyofficeIntegrationPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ partial | ⬜ pending |
| 8-01-02 | 01 | 1 | MOD-02 | package | `cd packages/server && mvn -q -DskipITs package` | ✅ | ⬜ pending |
| 8-01-03 | 01 | 1 | CFG-01, MOD-02 | integration | `cd packages/server && mvn test && docker compose config` | ✅ | ⬜ pending |
| 8-02-01 | 02 | 1 | SVC-01 | service | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=OnlyofficeConfigServiceTest,DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentStatusServiceTest,AccessAuditServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ partial | ⬜ pending |
| 8-02-02 | 02 | 1 | SVC-01, DOC-01 | mvc+service | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs test` | ✅ | ⬜ pending |
| 8-02-03 | 02 | 1 | DOC-01 | package | `cd packages/server && mvn -q -DskipITs package` | ✅ | ⬜ pending |
| 8-03-01 | 03 | 2 | STOR-04 | storage | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=StorageProviderResolverTest,MinioDocumentStorageStrategyTest,CosDocumentStorageStrategyTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ partial | ⬜ pending |
| 8-03-02 | 03 | 2 | STOR-04, DOC-01 | service+docs | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs test` | ✅ | ⬜ pending |
| 8-03-03 | 03 | 2 | CFG-01, STOR-04, DOC-01 | integration | `cd packages/server && mvn test && docker compose config` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] 明确 `dev / test / prod` profile 的激活方式，以及 `windows-debug` 与正式 profile 的关系
- [ ] 明确 service 接口化后 controller 和其他 service 都通过接口注入的人工核对路径
- [ ] 明确 COS 配置项、路由示例和回归测试入口的人工核对路径

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 新接手开发者能看懂如何在 `dev / test / prod` 间切换配置 | CFG-01 | 需要核对 README / 配置矩阵是否真能指导使用 | 阅读 `application.yml`、README 和配置矩阵，确认 profile 选择方式、默认值和本地调试覆盖关系都清楚 |
| service 包接口化后调用边界仍然清晰 | SVC-01 | 需要人工确认接口不是空壳重命名 | 抽查 controller、config、service 的构造注入，确认依赖的是接口而不是 `impl` |
| COS 扩展说明足以支持后续接入 | STOR-04 | 需要同时核对代码、配置和文档三处是否一致 | 检查 `StorageProvider`、配置样例、README / 配置矩阵中的 COS 说明是否使用同一组配置键和 provider 名称 |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures all manual-only paths
- [x] No watch-mode flags
- [x] Feedback latency < 360s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-26 for planning
