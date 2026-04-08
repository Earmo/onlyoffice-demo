---
phase: 12
slug: home-layout-delete-and-recent-edit-sorting
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-31
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Backend quick run** | `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataRepositoryTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,AccessAuditServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| **Frontend** | Vitest + Vue Test Utils + Element Plus |
| **Frontend quick run** | `cd packages/web && corepack pnpm test -- --run` |
| **Frontend build verification** | `cd packages/web && corepack pnpm build` |
| **Estimated runtime** | ~120-300 seconds |

---

## Sampling Rate

- **After every backend delete/list contract task commit:** Run `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataRepositoryTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,AccessAuditServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- **After every frontend homepage/list task commit:** Run `cd packages/web && corepack pnpm test -- --run`
- **After every plan wave:** Run `cd packages/web && corepack pnpm build`
- **After full phase execution:** Run backend quick run + frontend quick run + frontend build
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | PH12-RECENT-01 | repository+mvc | `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataRepositoryTest,DocumentApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | ✅ | ⬜ pending |
| 12-01-02 | 01 | 1 | PH12-DELETE-01 | service+mvc+runtime | `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,AccessAuditServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | ✅ | ⬜ pending |
| 12-02-01 | 02 | 1 | PH12-UI-01 | frontend-layout | `cd packages/web && corepack pnpm test -- --run` + `cd packages/web && corepack pnpm build` | ✅ partial | ⬜ pending |
| 12-02-02 | 02 | 1 | PH12-UI-01 | component-ui | `cd packages/web && corepack pnpm test -- --run` | ✅ partial | ⬜ pending |
| 12-03-01 | 03 | 2 | PH12-RECENT-01 | frontend-page | `cd packages/web && corepack pnpm test -- --run` + `cd packages/web && corepack pnpm build` | ✅ partial | ⬜ pending |
| 12-03-02 | 03 | 2 | PH12-DELETE-01 | frontend-regression | `cd packages/web && corepack pnpm test -- --run` + `cd packages/web && corepack pnpm build` | ✅ partial | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] 明确最近文档真相源采用“独立接口”还是“列表响应附带字段”，避免执行时一半用分页列表、一半用独立数据
- [ ] 明确删除仍有活跃编辑会话的文档时采用“拒绝删除”策略，并在前端同步禁用或隐藏入口
- [ ] 明确归档文档在详情 / 预览 / 编辑 / 下载链路上的统一返回语义（建议 `404`）

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 首页桌面端双栏比例与贴底布局符合预期 | PH12-UI-01 | 需要在真实浏览器视口里确认左右主盒子是否撑满到底边，以及移动端是否正确回落到单列 | 在桌面宽度打开工作台首页，确认左栏约占 `1/5`、右栏约占 `4/5`，左右主盒子均能贴近页面底部；在窄屏下确认页面回落成单列 |
| 删除文档后同时从主列表和左侧最近文档消失 | PH12-DELETE-01 | 需要串起删除确认、后端归档、列表刷新和最近文档刷新，不适合只靠单点测试判断 | 在工作台删除一份非编辑中的文档，确认当前页列表和左侧最近文档都不再出现该文档，且直接访问旧的预览 / 编辑链接会收到错误 |
| 最近文档与主列表都按最近编辑时间倒序 | PH12-RECENT-01 | 需要人工确认“打开/保存后排序变化”是否符合产品语义 | 准备两份文档，先后进入编辑或触发保存，返回工作台后确认主列表与左侧最近文档都把最近操作的文档排在前面 |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures the recent-doc source and delete-guard decisions
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-31 for planning
