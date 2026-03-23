---
phase: 3
slug: user-context-integration
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-23
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + MyBatis-Flex + MockMvc |
| **Config file** | `packages/server/pom.xml` |
| **Quick run command** | `cd packages/server && mvn -q -DskipITs test` |
| **Full suite command** | `cd packages/server && mvn test` |
| **Estimated runtime** | ~60-150 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd packages/server && mvn -q -DskipITs test`
- **After every plan wave:** Run `cd packages/server && mvn test`
- **Before `$gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 3-01-01 | 01 | 1 | USER-02 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=AccessContextPropertiesTest test` | ❌ W0 | ⬜ pending |
| 3-01-02 | 01 | 1 | USER-01, USER-02 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=HeaderAccessContextProviderTest,JwtAccessContextProviderTest,AccessContextResolverTest test` | ❌ W0 | ⬜ pending |
| 3-01-03 | 01 | 1 | USER-01, USER-02 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=AccessContextErrorHandlingTest test` | ❌ W0 | ⬜ pending |
| 3-02-01 | 02 | 2 | USER-01, USER-03 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest test` | ✅ partial | ⬜ pending |
| 3-02-02 | 02 | 2 | USER-01, USER-03 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test` | ✅ partial | ⬜ pending |
| 3-02-03 | 02 | 2 | USER-01, USER-03 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=OnlyofficeConfigServiceTest test` | ❌ W0 | ⬜ pending |
| 3-03-01 | 03 | 3 | USER-03 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=AccessAuditServiceTest,AccessAuditRepositoryTest test` | ❌ W0 | ⬜ pending |
| 3-03-02 | 03 | 3 | USER-02, USER-03 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=CustomAccessContextProviderOverrideTest test` | ❌ W0 | ⬜ pending |
| 3-03-03 | 03 | 3 | USER-01, USER-02, USER-03 | integration | `cd packages/server && mvn test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/context/AccessContextPropertiesTest.java` — 覆盖 provider 顺序、fallback 开关、JWT/header 配置绑定
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/context/HeaderAccessContextProviderTest.java` — 覆盖请求头解析与部分字段补齐
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/context/JwtAccessContextProviderTest.java` — 覆盖 JWT claim 到访问上下文的映射
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/context/AccessContextResolverTest.java` — 覆盖 provider 顺序、完全缺失时报错、部分缺失 fallback
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/context/AccessContextErrorHandlingTest.java` — 覆盖 4xx 错误语义
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigServiceTest.java` — 覆盖 editor config 中 user 与最小 permissions map 映射
- [ ] `packages/server/onlyoffice-integration-data/src/test/java/com/earmo/onlyoffice/integration/data/repository/AccessAuditRepositoryTest.java` — 覆盖轻量审计事件表写入与查询

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 最小接入文档已说明 header / jwt / custom provider 三种接入方式 | USER-02 | 需要人工核对接入说明是否完整 | 检查 `docs/minimal-integration.md`，确认包含 header、jwt、自定义 provider 覆盖和优先级配置说明 |
| 默认用户 fallback 受 profile + 显式开关共同控制 | USER-01, USER-02 | 需要同时核对配置和文档语义 | 检查 `application.yml` 与相关配置类，确认没有继续把默认用户当作永久安全兜底 |
| callback 被记录为 system event，而不是伪装成人类用户 | USER-03 | 需要阅读事件模型和文档说明 | 检查审计实体、事件类型和最小接入文档，确认 callback 事件来源标记为 `system` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-23
