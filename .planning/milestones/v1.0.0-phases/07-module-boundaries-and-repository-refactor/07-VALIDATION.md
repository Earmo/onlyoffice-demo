---
phase: 7
slug: module-boundaries-and-repository-refactor
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-23
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Mockito |
| **Config file** | `packages/server/pom.xml` |
| **Quick run command** | `cd packages/server && mvn -q -DskipITs test` |
| **Full suite command** | `cd packages/server && mvn test` |
| **Estimated runtime** | ~45-120 seconds |

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
| 7-01-01 | 01 | 1 | ARCH-04, MOD-01 | build | `cd packages/server && mvn -q -DskipITs test` | ✅ | ⬜ pending |
| 7-01-02 | 01 | 1 | ARCH-04 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=*Application* test` | ❌ W0 | ⬜ pending |
| 7-01-03 | 01 | 1 | MOD-01 | packaging | `cd packages/server && mvn -q -DskipITs package` | ✅ | ⬜ pending |
| 7-02-01 | 02 | 2 | DATA-01 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=*RepositoryTest test` | ❌ W0 | ⬜ pending |
| 7-02-02 | 02 | 2 | DATA-02 | migration | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentMetadataMapperTest test` | ✅ | ⬜ pending |
| 7-02-03 | 02 | 2 | DATA-01, DATA-02 | service | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentMetadataServiceTest test` | ✅ | ⬜ pending |
| 7-03-01 | 03 | 3 | MOD-01 | config | `cd packages/server && mvn -q -DskipITs test` | ✅ | ⬜ pending |
| 7-03-02 | 03 | 3 | ARCH-04, MOD-01 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test` | ✅ | ⬜ pending |
| 7-03-03 | 03 | 3 | ARCH-04, DATA-01, DATA-02, MOD-01 | full | `cd packages/server && mvn test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `packages/server/**/src/test/java/**/OnlyofficeIntegrationStarterApplicationTest.java` — 覆盖 service 模块应用上下文启动
- [ ] `packages/server/**/src/test/java/**/DocumentMetadataRepositoryTest.java` — 覆盖 repository 自定义查询入口
- [ ] `packages/server/**/src/test/resources/application.yml` — 覆盖模块拆分后的测试数据源与 Flyway 配置

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Dockerfile 指向新的 service 模块 boot jar | ARCH-04, MOD-01 | 需要人工确认打包产物路径与镜像入口 | 检查 `packages/server/Dockerfile`，确认 `COPY` 的 jar 路径来自 `onlyoffice-integration-service/target/`，且不再包含 `onlyoffice-demo-server` |
| demo 命名没有继续保留在核心构建与配置入口 | MOD-01 | 需要跨模块阅读 POM、配置和 README | 运行 `rg -n "onlyoffice-demo|OnlyofficeDemo|demo:" packages/server README.md docs --glob "!**/target/**"`，确认结果只剩历史文档或明确保留说明 |
| 生成的 APT 表定义来自实体变更而不是手工修改 | DATA-02 | `target` 目录不应成为人工维护对象 | 删除 `packages/server/**/target/generated-sources` 后重新编译，确认新的 `*TableDef` 常量反映 `*_time` / `*_user` 命名 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-23
