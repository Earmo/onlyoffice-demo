---
gsd_state_version: 1.0
milestone: v1.1.0
milestone_name: AI 对话式文档辅助生成
current_phase: 14 (llm-conversation-chain) — READY
status: executing
stopped_at: Phase 14 context updated; replanning required
last_updated: "2026-04-20T11:01:24.124Z"
last_activity: 2026-04-20 -- planned Phase 14 AI conversation chain
progress:
  total_phases: 3
  completed_phases: 1
  total_plans: 6
  completed_plans: 3
  percent: 50
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-20)

**Core value:** 任意上层系统都应该能以低耦合方式接入一个可分布式部署的文档编辑服务，让用户先看到自己的文档列表，再安全地选择、上传、打开并保存文档。
**Current focus:** Phase 14 — 大模型对话链路

## Current Position

Milestone: v1.1.0 — ACTIVE
Current phase: 14 (llm-conversation-chain) — READY
Plan: 1 of 3
Status: Ready to execute Phase 14
Last activity: 2026-04-20 -- planned Phase 14 AI conversation chain

Progress: [###-------] 33%

## Milestone Snapshot

**Milestone name:** AI 对话式文档辅助生成

**Goal:**
把编辑页右侧 `编辑运行态` 抽屉升级为 AI 对话工作台，让用户围绕当前选区完成对话生成、章节定位和内容写回。

**Target features:**

- 获取编辑中选中的文本项
- 对接大语言模型对话 API
- 将对话返回结果插入到光标选定处
- 快速定位到章节标题

## Accumulated Context

### Decisions

- 运行时地址采用 `publicBaseUrl`、`internalBaseUrl`、`documentServerUrl` 分离模型
- 文档元数据进入共享数据库模型，`documentId` 成为内部稳定主键
- 对外契约改为 headless-first，`/api/documents` 与 ONLYOFFICE 运行时接口分层
- 前端主界面统一收口到 Element Plus 组件体系
- v1.1.0 先验证“选区对话 + 定点写回”的 AI 编辑增强闭环，再考虑更重的权限、版本与 webhook 需求
- Phase 13 已实际落地隐藏 bridge plugin、选区抓取、章节目录刷新/跳转与 AI-ready 右侧工作台
- 预览态同样挂载 bridge plugin，保证编辑页与预览页共用一套选区/目录能力入口
- Phase 14 采用后端供应商中立 AI 代理层，前端只消费本项目归一化对话接口
- Phase 14 的多轮对话状态仅保留在当前编辑页会话内存中，不做跨文档或跨刷新持久化
- Phase 14 不实现流式输出与文档写回；写回能力明确留到 Phase 15
- Phase 14 已拆成三份计划：后端 AI 代理契约、前端消息线程、集成回归与交接准备

### Blockers/Concerns

- 真实部署前仍需补齐 AI provider 的 `baseUrl/apiKey/model` 实际配置值。
- 需要在 planning 阶段明确统一 DTO 形状、消息数量上限以及错误码映射策略。
- 当前机器上的 `npm/corepack` 与 `mvn` 包装命令存在环境级异常，本次 Phase 13 仅能根据仓库实现和测试文件回填状态，无法直接复跑标准 verify 命令。
- Phase 14 需要复用现有 bridge adapter，不应再新开一套宿主页与 ONLYOFFICE 之间的传输协议。

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260326-dmt | 做一个适配 Windows 本地调试的 docker-compose.debug.yml，将后端本地调试配置改成 yaml 文件，并把本地调试事项更新到文档里 | 2026-03-26 | 18335f5 | [260326-dmt-windows-docker-compose-debug-yml-yaml](./quick/260326-dmt-windows-docker-compose-debug-yml-yaml/) |
| 260327-cjb | packages\web前端也要加上完整的代码注释 | 2026-03-27 | d5fab0b | [260327-cjb-packages-web](./quick/260327-cjb-packages-web/) |
| 260327-db | 给数据库所有的表和字段都加上 comment 注释 | 2026-03-27 | 251aeaf | [260327-db-table-column-comments](./quick/260327-db-table-column-comments/) |
| 260327-pa9 | 优化编辑页侧边栏抽屉与布局自适应 | 2026-03-27 | 61e91f4 | [260327-pa9-optimize-editor-sidebar-drawer-layout](./quick/260327-pa9-optimize-editor-sidebar-drawer-layout/) |
| 260327-pw8 | 统一右侧控制台打开按鈕为侧边栏条纹样式 | 2026-03-27 | a5e8201 | [260327-pw8-unify-console-toggle-strip](./quick/260327-pw8-unify-console-toggle-strip/) |
| 260330-dq4 | 编辑器文档标题面板默认展开 | 2026-03-30 | e6b9a87 | [260330-dq4-editor-heading-panel-default-open](./quick/260330-dq4-editor-heading-panel-default-open/) |
| 260331-kj3 | 重构首页双栏布局与开始编辑入口 | 2026-03-31 | dfc2971 | [260331-kj3-homepage-two-column-layout](./quick/260331-kj3-homepage-two-column-layout/) |
| 260401-eyu | 首页支持自定义租户和当前用户 | 2026-04-01 | 398e23f | [260401-eyu-custom-context](./quick/260401-eyu-custom-context/) |
| 260409-q86 | 将右侧的章节标题样式改成跟左边类似的树形结构；点击对应章节时在页面顶部展示标题和段落（目前是在页尾）；章节标题和当前选取要跟运行态 / 现有动作一样可以展开和收起 | 2026-04-09 | b76a724 | [260409-q86-ui-update](./quick/260409-q86-ui-update/) |
| 260410-jso | 修复Maven构建输出乱码-设置UTF8编码 | 2026-04-10 | fea206f | [260410-jso-maven-utf8](./quick/260410-jso-maven-utf8/) |

## Session Continuity

Last session: 2026-04-20T11:01:24.118Z
Stopped at: Phase 14 context updated; replanning required
Resume file: .planning/phases/14-llm-conversation-chain/14-CONTEXT.md
