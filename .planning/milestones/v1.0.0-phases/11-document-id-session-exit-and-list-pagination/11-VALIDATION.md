---
phase: 11
slug: document-id-session-exit-and-list-pagination
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-31
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Backend quick run** | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,DocumentStatusServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| **Frontend** | Vitest + Vue Test Utils + Element Plus |
| **Frontend quick run** | `cd packages/web && corepack pnpm test -- --run` |
| **Frontend build verification** | `cd packages/web && corepack pnpm build` |
| **Estimated runtime** | ~120-300 seconds |

---

## Sampling Rate

- **After every backend document-id task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- **After every backend list-query task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentApiControllerTest,DocumentStatusServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- **After every frontend list/editor task commit:** Run `cd packages/web && corepack pnpm test -- --run`
- **After every plan wave:** Run `cd packages/web && corepack pnpm build`
- **After full phase execution:** Run backend quick run + frontend quick run + frontend build
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | PH11-ID-01 | service | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStorageServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | ✅ | ⬜ pending |
| 11-01-02 | 01 | 1 | PH11-ID-01 | mvc+model | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentApiControllerTest,DocumentMetadataServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | ✅ | ⬜ pending |
| 11-02-01 | 02 | 2 | PH11-LIST-01 | backend-contract | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentApiControllerTest,DocumentStatusServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | ✅ | ⬜ pending |
| 11-02-02 | 02 | 2 | PH11-LIST-01 | frontend-page | `cd packages/web && corepack pnpm test -- --run` + `cd packages/web && corepack pnpm build` | ✅ partial | ⬜ pending |
| 11-03-01 | 03 | 3 | PH11-SESSION-01 | service+mvc | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStatusServiceTest,DocumentControllerTest,DocumentApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | ✅ | ⬜ pending |
| 11-03-02 | 03 | 3 | PH11-SESSION-01 | frontend-regression | `cd packages/web && corepack pnpm test -- --run` + `cd packages/web && corepack pnpm build` | ✅ partial | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] 修复 Vitest 对 `element-plus/theme-chalk/base.css` 的导入基线，保证前端回归测试真正可运行
- [ ] 明确 `storage=available/unavailable` 在分页下的后端实现路径，确保 `documents` 与 `total` 一致
- [ ] 明确 create 接口对 legacy `documentId` 字段的兼容语义：保留但忽略，或同步移除

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 新建 / 上传 / 导入后返回 ULID 且标题仍保持用户语义 | PH11-ID-01 | 需要串起接口响应、数据库主键语义与页面显示，不适合只靠单点单测判断 | 在工作台分别执行“新建空白文档”“上传本地文档”“导入网络文档”，确认返回列表后标题仍是用户标题 / 原始文件名，而 URL 与内部 `documentId` 为服务端生成的 ULID |
| 返回列表后不再残留“编辑中” | PH11-SESSION-01 | 涉及编辑页离开动作、close session 接口、列表刷新与后端状态收敛的联动 | 打开文档进入编辑页，返回列表，确认列表重新加载后该文档在无其他活跃用户时不再显示 `editing` |
| 分页切换与筛选都由后端结果驱动 | PH11-LIST-01 | 需要人工确认切页、修改 page size、输入筛选条件后的网络请求和 UI 表现是否一致 | 在工作台切换页码、改变 page size、输入搜索词和状态筛选，确认每次都重新请求 `/api/documents`，且页面只渲染当前页数据 |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures the current Vitest baseline gap
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-31 for planning
