# Requirements: OnlyOffice Document Service

**Defined:** 2026-04-08
**Milestone:** v1.1.0 AI 对话式文档辅助生成
**Core Value:** 任意上层系统都应该能以低耦合方式接入一个可分布式部署的文档编辑服务，让用户先看到自己的文档列表，再安全地选择、上传、打开并保存文档。

## v1.1.0 Requirements

### Editor Bridge

- [x] **BRDG-01**: 用户可以在 AI 对话窗口中抓取当前编辑器选中的文本片段作为本轮对话上下文
- [x] **BRDG-02**: 当编辑器当前没有选中文本时，用户可以收到明确提示并重新抓取选区
- [x] **BRDG-03**: 用户可以在 AI 对话窗口中看到当前文档的章节标题列表
- [x] **BRDG-04**: 用户可以点击章节标题并快速定位到对应文档位置

### AI Conversation

- [ ] **CHAT-01**: 用户可以在右侧 AI 对话窗口输入问题，并将问题与选中文本一起发送给系统配置的大模型接口
- [ ] **CHAT-02**: 用户可以看到 AI 请求中的加载态、成功态与失败态，并在失败后重试
- [ ] **CHAT-03**: 用户可以在当前编辑会话内保留最近几轮对话上下文，而不必每次重新复制选区
- [ ] **CHAT-04**: 系统可以通过后端代理安全调用已配置的大模型对话 API，而不向浏览器暴露真实密钥

### AI Conversation Variants

- [x] **PH17-01**: 后端持久化 `document_llm_message_variant`，并为 assistant message 维护 `active_variant_index`
- [x] **PH17-02**: 首次生成创建 assistant message + variant 0；重新生成在同一 assistant message 下新增 variant，不新增重复 user/assistant 消息
- [x] **PH17-03**: SSE、request status、session detail DTO 暴露 `variantId`、`variantIndex`、`activeVariantIndex` 和必要的 variants 数组
- [x] **PH17-04**: prompt history 对每个 assistant message 只使用 active variant 文本
- [x] **PH17-05**: 前端消息 entry 使用 `variants + activeVariantIndex`，展示、reasoning、meta、复制、写回、重新生成均读取 active variant
- [x] **PH17-06**: 既有 assistant_text 数据迁移或兼容为 variant 0，旧会话加载不空白
- [x] **PH17-07**: 失败或取消的 regenerate variant 不破坏既有 completed 版本，并有自动化测试覆盖 active 行为
- [ ] **PH17-08**: docs/llm-workbench-phase14.md 记录多版本 regenerate 数据模型、协议、UI 行为和验证方式
- [x] **PH17-09**: regenerate、variant 创建、active 切换、terminal 状态具备 info 级可观测日志，且不记录敏感正文或密钥

### Content Writeback

- [ ] **WRIT-01**: 用户可以将任意一条 AI 回复插入到当前光标位置或替换当前选区
- [ ] **WRIT-02**: 插入 AI 回复后，当前编辑会话保持在线，页面不刷新，用户可以继续编辑或继续对话

## vNext Requirements

### AI Authoring Expansion

- **AIX-01**: 用户可以在同一窗口中切换多个模型或 prompt 模板
- **AIX-02**: 用户可以查看和恢复历史 AI 对话记录
- [x] **AIX-03**: 用户可以流式看到模型逐段输出，而不是等待完整结果 — Phase 16
- **AIX-04**: 用户可以基于整篇文档或知识库进行检索增强生成

## Out of Scope

| Feature | Reason |
|---------|--------|
| 浏览器直连大模型厂商 API | 真实密钥不能暴露在前端 |
| 一键自动改写全文 | 风险过高，超出本次“选区辅助生成”的可控边界 |
| 多模型路由与成本配额中心 | 需要独立的配置与治理设计，不适合本里程碑并入 |
| 对话记录跨文档持久化 | 先验证单次编辑会话内体验，再决定是否建长期存储 |
| 把 AI 抽屉同步扩展到首页或预览页 | 当前只聚焦文档编辑页主工作流 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| BRDG-01 | Phase 13 | Complete |
| BRDG-02 | Phase 13 | Complete |
| BRDG-03 | Phase 13 | Complete |
| BRDG-04 | Phase 13 | Complete |
| CHAT-01 | Phase 14 | Pending |
| CHAT-02 | Phase 14 | Pending |
| CHAT-03 | Phase 14 | Pending |
| CHAT-04 | Phase 14 | Pending |
| WRIT-01 | Phase 15 | Pending |
| WRIT-02 | Phase 15 | Pending |
| AIX-03 | Phase 16 | Complete |
| PH17-01 | Phase 17 | Complete |
| PH17-02 | Phase 17 | Complete |
| PH17-03 | Phase 17 | Complete |
| PH17-04 | Phase 17 | Complete |
| PH17-05 | Phase 17 | Complete |
| PH17-06 | Phase 17 | Complete |
| PH17-07 | Phase 17 | Complete |
| PH17-08 | Phase 17 | Pending |
| PH17-09 | Phase 17 | Complete |

**Coverage:**
- v1.1.0 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0

---
*Requirements defined: 2026-04-08*
*Last updated: 2026-04-27*
