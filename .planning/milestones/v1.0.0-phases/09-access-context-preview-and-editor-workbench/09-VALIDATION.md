---
phase: 9
slug: access-context-preview-and-editor-workbench
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-27
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Access context regression** | `AccessContextResolverTest`, `HeaderAccessContextProviderTest`, `JwtAccessContextProviderTest`, `CustomAccessContextProviderOverrideTest`, `AccessContextErrorHandlingTest` |
| **Runtime/controller quick run** | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentStatusServiceTest,DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| **Frontend** | Vitest + Vue Test Utils |
| **Frontend quick run** | `cd packages/web && corepack pnpm test -- --run` |
| **Frontend build verification** | `cd packages/web && corepack pnpm build` |
| **Estimated runtime** | ~120-300 seconds |

---

## Sampling Rate

- **After every access-context strategy task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=AccessContextResolverTest,HeaderAccessContextProviderTest,JwtAccessContextProviderTest,CustomAccessContextProviderOverrideTest,AccessContextErrorHandlingTest -Dsurefire.failIfNoSpecifiedTests=false test`
- **After every preview/workbench UI task commit:** Run `cd packages/web && corepack pnpm test -- --run`
- **After every session-lifecycle task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentStatusServiceTest,DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- **After every plan wave:** Run `cd packages/web && corepack pnpm build`
- **After full phase execution:** Run backend quick run + frontend test + frontend build
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 9-01-01 | 01 | 1 | USER-04 | strategy | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=AccessContextResolverTest,HeaderAccessContextProviderTest,JwtAccessContextProviderTest,CustomAccessContextProviderOverrideTest,AccessContextErrorHandlingTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ partial | ⬜ pending |
| 9-01-02 | 01 | 1 | USER-04 | mvc+docs | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ partial | ⬜ pending |
| 9-01-03 | 01 | 1 | USER-04 | integration | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs test` | ✅ | ⬜ pending |
| 9-02-01 | 02 | 1 | LIB-04, UI-01 | frontend-page | `cd packages/web && corepack pnpm test -- --run` | ✅ partial | ⬜ pending |
| 9-02-02 | 02 | 1 | LIB-04, UI-01 | frontend-build | `cd packages/web && corepack pnpm build` | ✅ | ⬜ pending |
| 9-02-03 | 02 | 1 | LIB-04, UI-01 | backend+frontend | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` + `cd packages/web && corepack pnpm test -- --run` | ✅ | ⬜ pending |
| 9-03-01 | 03 | 2 | EDIT-03 | service | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentStatusServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | ✅ partial | ⬜ pending |
| 9-03-02 | 03 | 2 | EDIT-03, LIB-04 | mvc+frontend | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=DocumentControllerTest,DocumentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` + `cd packages/web && corepack pnpm test -- --run` | ✅ | ⬜ pending |
| 9-03-03 | 03 | 2 | EDIT-03, UI-01 | full-phase | `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs test` + `cd packages/web && corepack pnpm build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] 明确 Phase 9 不重建访问上下文体系，而是复用并收口现有 `AccessContextProvider` SPI
- [ ] 明确预览页与编辑页的职责边界，避免“只是按钮改名，行为没分开”
- [ ] 明确“编辑中”状态的真相源将引入会话生命周期，而不是继续只依赖 callback 事件

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 预览模式和编辑模式在产品意图上真正分离 | LIB-04 | 需要人工判断页面文案、操作入口和只读/可编辑语义是否清晰 | 从列表页分别点击“查看文件”和“编辑文档”，确认进入的是两个不同的页面意图 |
| 控制台改为同层固定布局后，编辑页仍然可用 | UI-01 | 需要人工核对桌面端与窄屏下的真实布局体验 | 打开编辑页，展开/收起顶部提示区和控制台，确认编辑器区域是被布局挤压而不是被遮罩覆盖 |
| 返回列表或切换文档后，“编辑中”状态能收敛 | EDIT-03 | 涉及前端离开行为、后端会话结束和列表摘要同步，需要人工串联 | 打开文档进入编辑，返回列表或切到另一文档，确认原文档在没有活跃编辑用户时不再显示“编辑中” |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures all manual-only paths
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-27 for planning
