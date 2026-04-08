---
phase: 2
slug: storage-strategy-layer
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-23
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (MinIO via GenericContainer) |
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
| 2-01-01 | 01 | 1 | STOR-01 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=OnlyofficeIntegrationPropertiesTest test` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 1 | STOR-01 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=LocalDocumentStorageStrategyTest test` | ❌ W0 | ⬜ pending |
| 2-01-03 | 01 | 1 | STOR-01 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStorageServiceTest,DocumentApiControllerTest test` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 2 | STOR-02 | config | `docker compose config` | ✅ | ⬜ pending |
| 2-02-02 | 02 | 2 | STOR-02 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=MinioDocumentStorageStrategyTest test` | ❌ W0 | ⬜ pending |
| 2-02-03 | 02 | 2 | STOR-01, STOR-02 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest test` | ❌ W0 | ⬜ pending |
| 2-03-01 | 03 | 3 | STOR-03 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=StorageProviderResolverTest test` | ❌ W0 | ⬜ pending |
| 2-03-02 | 03 | 3 | STOR-02, STOR-03 | config | `docker compose config` | ✅ | ⬜ pending |
| 2-03-03 | 03 | 3 | STOR-01, STOR-02, STOR-03 | integration | `cd packages/server && mvn test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationPropertiesTest.java` — 覆盖 `storage.default-provider`、`storage.local.root`、`storage.minio.*` 绑定
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/local/LocalDocumentStorageStrategyTest.java` — 覆盖 local provider 的读写与非法 key 防护
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/minio/MinioDocumentStorageStrategyTest.java` — 覆盖 MinIO 的 put/get/stat/remove 行为
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/StorageProviderResolverTest.java` — 覆盖默认 provider、按 tenant 路由、按 sourceSystem 路由
- [ ] `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java` — 覆盖 callback 成功/失败与旧版本保留语义

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| compose demo 同时包含 `postgres + minio + onlyoffice + server + web` | STOR-02 | 需要核对联调编排、环境变量和启动依赖 | 运行 `docker compose config`，确认 `minio` 服务存在，`server` 环境变量包含 `ONLYOFFICE_INTEGRATION_STORAGE_DEFAULT_PROVIDER`、`ONLYOFFICE_INTEGRATION_STORAGE_MINIO_ENDPOINT`、`ONLYOFFICE_INTEGRATION_STORAGE_MINIO_BUCKET` |
| local profile 仍可单机开发，而正式联调默认转向 MinIO | STOR-02, STOR-03 | 需要人工阅读 profile 与 compose 组合 | 检查 `application.yml` 或 profile 文件，确认 `dev/test` 默认 provider 可回退 `local`，而 compose/prod 使用 `minio` |
| 文档列表在对象丢失时仍可看到文档，但能暴露异常可见性 | STOR-01 | 需要结合 API 投影与产品语义人工确认 | 阅读 `DocumentApiController`、`DocumentSummaryResponse` 和相关测试，确认不会隐藏元数据记录，且响应中存在 `storageAvailable` 字段 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-23
