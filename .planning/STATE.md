---
gsd_state_version: 1.0
milestone: v1.1.0
milestone_name: AI 对话式文档辅助生成
status: in_progress
last_updated: "2026-04-28T03:21:12.164Z"
last_activity: 2026-04-28
progress:
  total_phases: 6
  completed_phases: 5
  total_plans: 18
  completed_plans: 15
  percent: 83
---

## Accumulated Context

Last activity: 2026-04-28

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260427-o03 | 在新建对话的首次对话后给当前会话起个符合本次对话主体的新会话名称 | 2026-04-27 | uncommitted | [260427-o03-auto-session-title](./quick/260427-o03-auto-session-title/) |
| 260427-oq8 | 后端数据库表增加一个最后对话时间，/api/llm/sessions查询列表按照最后对话时间排序 | 2026-04-27 | uncommitted | [260427-oq8-api-llm-sessions](./quick/260427-oq8-api-llm-sessions/) |

### Roadmap Evolution

- Phase 17 added: 将当前 Java 后端的 AI 对话重新生成功能改造成类似 ChatGPT 的多版本切换模式
- Phase 14.3 inserted after Phase 14: heartbeat 仍由浏览器每 5 秒续期，避免服务端 active timeout。但是目前已经在 Phase 14 引入 SSE，所以不要浏览器每 5 秒轮询 /editing-sessions/heartbeat 接口；同时更新文档，避免后续 phase 继续以旧文档为基础反复修改代码 (URGENT)

### Decisions

- Phase 17 Plan 01 使用 `document_llm_message_variant` 表承载 assistant 多版本回复，`document_llm_message.active_variant_index` 保存当前版本。
- LLM variant DTO 新字段保留向后兼容构造器，避免破坏既有 controller/service 调用点。
- Phase 17 Plan 01 的 service 层测试验证使用 data+service reactor，避免本机旧 data SNAPSHOT artifact 干扰编译。

### Performance Metrics

| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| 17-java-ai-chatgpt | 01 | 11min | 3 | 15 |

### Last Session

- **Completed:** 17-01-PLAN.md
- **Stopped At:** None
