---
phase: 4
slug: document-library-experience
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-25
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Frontend** | Vue 3 + Vite |
| **Backend** | Spring Boot Test + MockMvc + JUnit 5 |
| **Frontend quick run** | `cd packages/web && pnpm build` |
| **Backend quick run** | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test` |
| **Full suite command** | `cd packages/server && mvn test` |
| **Estimated runtime** | ~60-180 seconds |

---

## Sampling Rate

- **After every frontend task commit:** Run `cd packages/web && pnpm build`
- **After every backend/API task commit:** Run `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test`
- **After every plan wave:** Run `cd packages/server && mvn test`
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 4-01-01 | 01 | 1 | LIB-01 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test` | ✅ partial | ⬜ pending |
| 4-01-02 | 01 | 1 | LIB-01, LIB-02 | build | `cd packages/web && pnpm build` | ✅ | ⬜ pending |
| 4-01-03 | 01 | 1 | LIB-01 | build | `cd packages/web && pnpm build` | ✅ | ⬜ pending |
| 4-02-01 | 02 | 2 | LIB-02 | build | `cd packages/web && pnpm build` | ✅ | ⬜ pending |
| 4-02-02 | 02 | 2 | LIB-02 | build | `cd packages/web && pnpm build` | ✅ | ⬜ pending |
| 4-02-03 | 02 | 2 | LIB-02 | manual | `cd packages/web && pnpm build` | ❌ W0 | ⬜ pending |
| 4-03-01 | 03 | 3 | LIB-03 | mvc | `cd packages/server && mvn -q -DskipITs -Dtest=DocumentApiControllerTest test` | ✅ partial | ⬜ pending |
| 4-03-02 | 03 | 3 | LIB-03 | build | `cd packages/web && pnpm build` | ✅ | ⬜ pending |
| 4-03-03 | 03 | 3 | LIB-01, LIB-02, LIB-03 | integration | `cd packages/server && mvn test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] 明确首页默认是工作台，而不是自动打开编辑器的手动验证路径
- [ ] 明确从列表进入 `/editor/:id`、返回列表、切换文档确认的手动验证路径
- [ ] 明确新建/上传/导入成功后回流列表并高亮结果的手动验证路径

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 首页默认进入文档工作台而不是直接打开编辑器 | LIB-01 | 当前无前端 E2E 测试基线 | 启动 web + server，访问 `/`，确认首屏是工作台列表和主操作区 |
| 从列表进入编辑页、从编辑页返回列表、切换文档确认 | LIB-02 | 需要真实观察路由和确认交互 | 访问 `/editor/:id`，检查返回入口；在编辑页触发切换文档，确认弹出明确提示 |
| 新建/上传/导入成功后回流列表并高亮结果 | LIB-03 | 需要观察前端状态回流和高亮行为 | 从首页执行三类创建动作，确认结果回到列表并被高亮，而不是直接静默跳转 |

---

## Validation Sign-Off

- [x] All tasks have `<verify>` or explicit manual validation mapping
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 captures all manual-only paths
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-25
