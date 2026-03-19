---
phase: 1
slug: service-foundation
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-19
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `packages/server/pom.xml` |
| **Quick run command** | `cd packages/server && mvn -q -DskipITs test` |
| **Full suite command** | `cd packages/server && mvn test` |
| **Estimated runtime** | ~30-90 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd packages/server && mvn -q -DskipITs test`
- **After every plan wave:** Run `cd packages/server && mvn test`
- **Before `$gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | ARCH-01 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=DemoPropertiesTest test` | ❌ W0 | ⬜ pending |
| 1-01-02 | 01 | 1 | ARCH-01 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=OnlyofficeConfigServiceTest test` | ✅ | ⬜ pending |
| 1-01-03 | 01 | 1 | ARCH-01, ARCH-03 | config | `docker compose config` | ✅ | ⬜ pending |
| 1-02-01 | 02 | 1 | ARCH-02, ARCH-03 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentMetadataRepositoryTest test` | ❌ W0 | ⬜ pending |
| 1-02-02 | 02 | 1 | ARCH-02, ARCH-03 | integration | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentMetadataServiceTest test` | ❌ W0 | ⬜ pending |
| 1-02-03 | 02 | 1 | ARCH-03 | migration | `cd packages/server && mvn -q -DskipITs test` | ✅ | ⬜ pending |
| 1-03-01 | 03 | 2 | ARCH-02 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test` | ❌ W0 | ⬜ pending |
| 1-03-02 | 03 | 2 | ARCH-02 | unit | `cd packages/server && mvn -q -DskipITs -Dtest=RequestContextResolverTest test` | ❌ W0 | ⬜ pending |
| 1-03-03 | 03 | 2 | ARCH-01, ARCH-02 | integration | `cd packages/server && mvn test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `packages/server/src/test/java/com/earmo/onlyoffice/demo/config/DemoPropertiesTest.java` — 覆盖 public/internal/document-server 新配置绑定
- [ ] `packages/server/src/test/java/com/earmo/onlyoffice/demo/persistence/DocumentMetadataRepositoryTest.java` — 覆盖文档主表基础读写
- [ ] `packages/server/src/test/java/com/earmo/onlyoffice/demo/service/DocumentMetadataServiceTest.java` — 覆盖元数据服务状态流转
- [ ] `packages/server/src/test/java/com/earmo/onlyoffice/demo/web/DocumentApiControllerTest.java` — 覆盖列表 / 创建 / 打开接口契约
- [ ] `packages/server/src/test/java/com/earmo/onlyoffice/demo/web/RequestContextResolverTest.java` — 覆盖标准化用户上下文解析

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| compose demo 同时暴露 `api service / official web / onlyoffice / database` 的联调结构 | ARCH-01 | 需要验证多容器编排与环境变量注入 | 运行 `docker compose config`，确认存在数据库服务、server 环境变量里包含 public/internal/document-server 相关配置键，且 `web` 仍保留反代入口 |
| nginx 聚合入口在保留前提下未重新耦合服务边界 | ARCH-01, ARCH-02 | 需要人工阅读反向代理规则 | 检查 `packages/web/nginx.conf`，确认 `/api/` 与 ONLYOFFICE 路径仍可代理，但前端静态资源入口没有重新硬编码文档文件地址 |
| ONLYOFFICE editor config 返回的 callback/file URL 与部署语义一致 | ARCH-01, ARCH-02 | 依赖运行中的 ONLYOFFICE 与服务地址 | 启动 compose demo，调用 editor-config 接口，检查返回 JSON 中 file URL 和 callback URL 为绝对地址，且分别符合 public/internal 设计 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-19
