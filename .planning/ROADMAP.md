# Roadmap: OnlyOffice Document Service

## Milestones

- ✅ **v1.0.0** — Phases 1-12, 36 plans, shipped 2026-04-08. See `.planning/milestones/v1.0.0-ROADMAP.md`.
- 🚧 **v1.1.0** — Phases 13-17, 19 requirements, started 2026-04-08.

## Current Milestone: v1.1.0 AI 对话式文档辅助生成

**Goal:** 把文档编辑页右侧 `编辑运行态` 抽屉升级为 AI 对话工作台，围绕当前选区实现“读取上下文 → 调用模型 → 结果写回 → 标题跳转”的完整闭环。

**Phases:** 6
**Requirements mapped:** 19 / 19
**Coverage:** Phase 17 requirements mapped

| # | Phase | Status | Goal | Requirements | Success Criteria |
|---|-------|--------|------|--------------|------------------|
| 13 | 编辑器运行态桥接 | ✅ Complete | 让 AI 抽屉可以读取选区、展示标题并驱动章节定位 | BRDG-01, BRDG-02, BRDG-03, BRDG-04 | 4 |
| 14 | 大模型对话链路 | ◆ Ready to Execute | 建立后端安全代理和前端多轮对话状态 | CHAT-01, CHAT-02, CHAT-03, CHAT-04 | 4 |
| 14.3 | SSE 接管编辑会话存活续期 | ✅ Complete | runtime-events SSE 接管编辑会话存活，移除浏览器 5 秒 heartbeat 轮询和 REST heartbeat 接口 | TBD | 5 |
| 15 | AI 写回与抽屉收口 | ○ Planned | 将 AI 回复插入当前光标/选区，并完成抽屉替换与验证 | WRIT-01, WRIT-02 | 4 |
| 16 | LLM 流式推送与深度思考 UI | ✅ Complete | 修复深度思考展示顺序、Markdown、折叠体验，并实现首 token 即 SSE 增量推送 | AIX-03 | 4 |
| 17 | 将当前 Java 后端的 AI 对话重新生成功能改造成类似 ChatGPT 的多版本切换模式 | ◆ Ready to Execute | 将重新生成改造成同一 assistant 消息下的多版本切换体验 | PH17-01, PH17-02, PH17-03, PH17-04, PH17-05, PH17-06, PH17-07, PH17-08, PH17-09 | 9 |

## Phase Details

### Phase 13: 编辑器运行态桥接

**Goal:** 在不破坏现有 ONLYOFFICE 编辑主链路的前提下，拿到 AI 抽屉所需的文档运行态能力，包括选区快照、章节结构和标题定位。

**Status:** Complete (recorded 2026-04-20 from implemented repo state and follow-up UI adjustments)

**Requirements:** BRDG-01, BRDG-02, BRDG-03, BRDG-04

**Success criteria:**
1. 用户打开右侧抽屉后可以主动抓取当前选中文本，并看到可读的选区预览或空选区提示。
2. 抽屉可以展示当前文档的章节标题列表，而不是依赖用户再打开 ONLYOFFICE 左侧导航面板查看。
3. 用户点击任意章节标题后，编辑器可以快速跳到对应位置，页面不刷新、编辑会话不断开。
4. 现有保存状态查询、编辑会话 close、runtime-events SSE 会话存活、远程图片插入链路不因桥接改造而失效；不得重新引入 `/editing-sessions/heartbeat` REST 轮询。

### Phase 14: 大模型对话链路

**Goal:** 提供安全、可配置的大模型对话 API 接入方式，并让右侧抽屉具备围绕选区工作的多轮对话体验。

**Status:** Planned (2026-04-20) — ready to execute

**Requirements:** CHAT-01, CHAT-02, CHAT-03, CHAT-04

**Success criteria:**
1. 用户在抽屉中输入问题后，系统可以把问题与当前选区上下文一起发送到后端大模型代理接口。
2. 抽屉内能正确表现发送中、返回成功、返回失败和重试等关键状态，不出现“静默失败”。
3. 同一编辑会话内至少保留最近几轮对话消息，用户无需重复粘贴同一选区背景。
4. 模型密钥和厂商接入细节仅保留在后端配置与服务层，浏览器请求中不暴露真实敏感信息。

### Phase 14.3: SSE 接管编辑会话存活续期 (INSERTED)

**Goal:** 在 Phase 14.1 已引入编辑运行态 SSE 的基础上，让 SSE 连接承担编辑会话存活续期，停止浏览器每 5 秒轮询 `/editing-sessions/heartbeat`，并移除对外 REST heartbeat 接口；同步修正旧规划文档，避免后续 phase 继续按旧 heartbeat 假设改代码。
**Requirements**: TBD
**Depends on:** Phase 14
**Plans:** 1/1 plans complete

Plans:
- [x] 14.3-01: 后端安全 runtime SSE liveness、前端 healthy SSE 停止 heartbeat、文档契约纠偏

### Phase 15: AI 写回与抽屉收口

**Goal:** 让 AI 回复真正回到文档编辑流中，形成“对话生成后立即落到当前编辑位置”的闭环，并完成本轮 UI 收口与验证。

**Requirements:** WRIT-01, WRIT-02

**Success criteria:**
1. 用户可以从 AI 消息列表中选择一条回复并插入到当前光标处，或直接替换先前选中的文本。
2. 插入完成后文档保持在当前编辑上下文中，不触发整页刷新，也不丢失当前编辑会话。
3. 原右侧 `编辑运行态` 抽屉完成职责迁移，AI 对话窗口成为编辑页默认的右侧工作台。
4. 自动化测试和手工验证覆盖选区抓取、模型调用、结果写回、章节跳转四条主链路。

### Phase 16: LLM 流式推送与深度思考 UI

**Goal:** 修复 AI 对话工作台中深度思考内容的展示顺序、Markdown 渲染和折叠体验，并把后端 LLM 响应推进到真正的实时增量推送：从收到首个正文 token 或 reasoning token 起即通过 AI SSE 下发给前端。

**Status:** Complete (2026-04-27) — verified

**Requirements:** AIX-03

**Success criteria:**
1. 深度思考内容展示在正文回复之前，进行中、完成态和历史消息顺序一致。
2. 深度思考内容渲染为 Markdown，并经过 DOMPurify 清洗，不再以纯文本 `<pre>` 展示。
3. 深度思考块默认折叠；生成中可见“深度思考中”入口，用户展开后可实时查看已收到内容。
4. 后端收到正文或 reasoning 增量后立即通过 AI SSE 推送，失败/取消时保留已收到的部分内容。

**Plans:** 3/3 plans complete

**Wave 1**
- [x] `16-01` — 后端 reasoning SSE 契约与部分内容持久化

**Wave 2 *(blocked on Wave 1 completion)***
- [x] `16-02` — 前端深度思考流式 UI 与 Markdown 安全渲染

**Wave 3 *(blocked on Wave 1 and Wave 2 completion)***
- [x] `16-03` — 协议文档、回归和全量验证收口

### Phase 17: 将当前 Java 后端的 AI 对话重新生成功能改造成类似 ChatGPT 的多版本切换模式

**Goal:** 将当前 Java 后端和前端 AI 工作台的重新生成功能改造成同一 assistant 消息下的多版本切换模式，保留请求审计、流式 reasoning、写回和失败取消语义。
**Status:** Ready to Execute (planned 2026-04-28)
**Requirements**: PH17-01, PH17-02, PH17-03, PH17-04, PH17-05, PH17-06, PH17-07, PH17-08, PH17-09
**Depends on:** Phase 16
**Plans:** 1/4 plans executed

Plans:
**Wave 1**
- [x] 17-01-PLAN.md — 后端 variant 持久化与 DTO 契约

**Wave 2** *(blocked on Wave 1 completion)*
- [ ] 17-02-PLAN.md — 后端 regenerate、stream、prompt history 语义

**Wave 3** *(blocked on Wave 2 completion)*
- [ ] 17-03-PLAN.md — 前端 variants entry、版本切换 UI 与操作路径

**Wave 4** *(blocked on Wave 3 completion)*
- [ ] 17-04-PLAN.md — 协议文档、回归验证和安全收口

## Next Up

**Execute Phase 17: 将当前 Java 后端的 AI 对话重新生成功能改造成类似 ChatGPT 的多版本切换模式**

`$gsd-execute-phase 17`
