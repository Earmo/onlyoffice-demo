---
phase: 6
slug: verification-and-delivery
status: passed
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-26
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Frontend** | Vitest + Vue Test Utils + jsdom |
| **Backend quick run** | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest,DocumentStatusServiceTest,OnlyofficeConfigServiceTest,DocumentStorageServiceTest,OnlyofficeImageServiceTest,StorageProviderResolverTest test` |
| **Frontend quick run** | `cd packages/web && corepack pnpm test -- --run` |
| **Frontend build run** | `cd packages/web && corepack pnpm build` |
| **Compose verification** | `docker compose config` |
| **Full delivery verify** | `npm run verify` |
| **Estimated runtime** | ~120-300 seconds |

---

## Sampling Rate

- **After every backend regression task commit:** Run `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest,DocumentStatusServiceTest,OnlyofficeConfigServiceTest,DocumentStorageServiceTest,OnlyofficeImageServiceTest,StorageProviderResolverTest test`
- **After every frontend test task commit:** Run `cd packages/web && corepack pnpm test -- --run`
- **After every frontend UI-affecting test task commit:** Run `cd packages/web && corepack pnpm build`
- **After every documentation / verify-entry task commit:** Run `docker compose config`
- **After every plan wave:** Run `cd packages/server && mvn test`
- **After full phase execution:** Run `npm run verify`
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 6-01-01 | 01 | 1 | QUAL-01 | service | `mvn --% -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentStatusServiceTest,OnlyofficeConfigServiceTest,DocumentStorageServiceTest,OnlyofficeImageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ | ✅ green |
| 6-01-02 | 01 | 1 | QUAL-01 | mvc | `mvn --% -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ | ✅ green |
| 6-01-03 | 01 | 1 | QUAL-01 | storage | `mvn --% -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=StorageProviderResolverTest,LocalDocumentStorageStrategyTest,MinioDocumentStorageStrategyTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ | ✅ green |
| 6-02-01 | 02 | 1 | QUAL-02 | frontend-setup | `cd packages/web && corepack pnpm test -- --run` | ✅ | ✅ green |
| 6-02-02 | 02 | 1 | QUAL-02 | frontend-page | `cd packages/web && corepack pnpm test -- --run` | ✅ | ✅ green |
| 6-02-03 | 02 | 1 | QUAL-02 | frontend-build | `cd packages/web && corepack pnpm build` | ✅ | ✅ green |
| 6-03-01 | 03 | 2 | QUAL-03 | root-verify | `npm run verify` | ✅ | ✅ green |
| 6-03-02 | 03 | 2 | QUAL-03 | docs | `docker compose config` | ✅ | ✅ green |
| 6-03-03 | 03 | 2 | QUAL-01, QUAL-02, QUAL-03 | integration | `npm run verify` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] 明确新接手开发者从根目录执行统一验证命令的手动验证路径
- [x] 明确工作台首页与编辑页前端测试覆盖的手动核对路径
- [x] 明确独立部署说明、微服务接入说明和配置矩阵三类交付文档的手动核对路径

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 新接手开发者按 README 顺序能完成一次完整验证 | QUAL-03 | 需要验证文档导航和执行顺序是否真的可跟随 | 从仓库根开始，按 README 推荐顺序执行统一验证命令，确认无需阅读 `.planning` 也能完成验证 |
| 前端测试覆盖的页面行为与真实 UI 语义一致 | QUAL-02 | 需要人工确认测试没有只验证实现细节 | 启动前端后人工核对首页列表、创建入口、编辑页返回/切换确认和保存状态区，确认与自动化测试关注点一致 |
| 独立部署 / 微服务接入 / 配置矩阵三类文档能支撑交付 | QUAL-03 | 需要人工检查文档结构、可读性和信息完整度 | 依次阅读 README、最小接入说明、交付文档和配置矩阵，确认三条主线清晰且不互相冲突 |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures all manual-only paths
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-26 after execution
