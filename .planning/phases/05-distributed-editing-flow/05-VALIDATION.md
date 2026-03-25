---
phase: 5
slug: distributed-editing-flow
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-25
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Runtime config check** | Docker Compose |
| **Quick state run** | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStatusServiceTest,DocumentMetadataServiceTest test` |
| **Protocol quick run** | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest,OnlyofficeConfigServiceTest test` |
| **Remote safety quick run** | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStorageServiceTest,OnlyofficeImageServiceTest test` |
| **Full suite command** | `cd packages/server && mvn test` |
| **Compose verification** | `docker compose config` |
| **Estimated runtime** | ~90-240 seconds |

---

## Sampling Rate

- **After every runtime-state task commit:** Run `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStatusServiceTest,DocumentMetadataServiceTest test`
- **After every callback / config task commit:** Run `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest,OnlyofficeConfigServiceTest test`
- **After every remote-resource task commit:** Run `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStorageServiceTest,OnlyofficeImageServiceTest test`
- **After every plan wave:** Run `cd packages/server && mvn test`
- **After full phase execution:** Run `docker compose config`
- **Max feedback latency:** 240 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 5-01-01 | 01 | 1 | EDIT-01, SAFE-03 | service | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStatusServiceTest,DocumentMetadataServiceTest test` | ✅ partial | ⬜ pending |
| 5-01-02 | 01 | 1 | EDIT-01, SAFE-03 | service | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStatusServiceTest,DocumentMetadataServiceTest test` | ✅ partial | ⬜ pending |
| 5-01-03 | 01 | 1 | SAFE-03 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest test` | ✅ partial | ⬜ pending |
| 5-02-01 | 02 | 2 | SAFE-01 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest test` | ✅ partial | ⬜ pending |
| 5-02-02 | 02 | 2 | EDIT-02 | service | `cd packages/server && mvn -q -DskipITs -Dtest=OnlyofficeConfigServiceTest test` | ✅ | ⬜ pending |
| 5-02-03 | 02 | 2 | EDIT-02, SAFE-01 | integration | `cd packages/server && mvn test` | ✅ | ⬜ pending |
| 5-03-01 | 03 | 3 | SAFE-02 | service | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStorageServiceTest,OnlyofficeImageServiceTest test` | ✅ | ⬜ pending |
| 5-03-02 | 03 | 3 | SAFE-02 | service | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentStorageServiceTest,OnlyofficeImageServiceTest test` | ✅ | ⬜ pending |
| 5-03-03 | 03 | 3 | SAFE-02, SAFE-03 | integration | `cd packages/server && mvn test && docker compose config` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] 明确 callback JWT 失败时的手动验证路径，确认返回明确 `4xx`
- [ ] 明确编辑页 `save-status` 在跨实例或模拟跨实例场景下的一致性验证路径
- [ ] 明确远程导入和图片代理被安全策略拒绝时的手动验证路径

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| callback JWT 验签失败返回明确拒绝 | SAFE-01 | 需要观察真实 HTTP 语义和错误提示 | 构造无效或缺失 token 的 callback 请求，确认返回 `4xx` 且运行状态有记录 |
| 编辑页跨实例读取到一致保存状态 | SAFE-03 | 当前无多实例自动化测试基线 | 在两实例或模拟切换场景下打开同一文档，确认 `save-status` 返回一致摘要和最近事件 |
| 远程导入 / 图片代理被安全策略拒绝时错误可解释 | SAFE-02 | 需要观察真实拒绝提示 | 用私网地址、超大响应或错误媒体类型测试，确认接口返回可读错误 |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures all manual-only paths
- [x] No watch-mode flags
- [x] Feedback latency < 240s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-25
