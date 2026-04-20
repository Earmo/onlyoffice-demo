# Phase 14: 大模型对话链路 - Context

**Gathered:** 2026-04-20
**Status:** Ready for replanning

<domain>
## Phase Boundary

本 phase 按最新讨论结果交付 4 件事：独立 `llm` 配置根下的可切换大模型代理、右侧抽屉正式对话工作台、按文档维度持久化的多会话消息线程，以及可取消/可重试的请求链路。Phase 15 仍负责把 AI 回复真正写回光标或选区；本 phase 不交付文档写回。

</domain>

<decisions>
## Implementation Decisions

### 模型策略、响应契约与配置根
- **D-01:** 后端采用可切换的策略模式组织 provider，默认 provider 走标准 OpenAI-compatible 接口，但整体设计不能把 Phase 14 锁死在单一厂商 SDK 上。
- **D-02:** 浏览器只消费本项目定义的 LLM DTO，不透传上游原始响应；所有厂商差异都由后端代理层吸收。
- **D-03:** 响应 DTO 需要保留 `usage`、`finishReason` 与 `providerResponseMeta`，用于前端展示扩展信息；`providerResponseMeta` 可以比当前上下文更丰富，但仍通过本项目 DTO 白名单下发，不能等价透传完整上游 payload，更不能包含密钥等敏感值。
- **D-04:** AI 相关配置从 `onlyoffice.integration.*` 中独立出来，改为 `llm.*` 配置根；该配置根不仅管理 provider/baseUrl/apiKey/model/timeout，也管理 Phase 14 的会话策略类配置。
- **D-05:** 当 LLM 未配置、被禁用或不可用时，右侧抽屉仍然可见；输入区必须禁用，并且既要在输入框上方给固定提示，也要在消息空态卡片中再次说明当前不可对话。

### 对话上下文边界
- **D-06:** 每次发送都必须绑定一份“明确快照”；这份快照可以是正常选区，也可以是显式记录的空选区快照。
- **D-07:** 如果用户在同一会话里重新抓取新选区，系统必须弹出确认，让用户决定“新开会话”还是“继续当前会话”。
- **D-08:** 没有抓到选区时仍允许首轮提问，但请求体必须显式标记当前是“无选区上下文”发送，不能伪装成已有正文快照。
- **D-09:** 当前活跃章节标题默认自动进入模型 payload，但界面上要明显提示本轮会带上该标题，并允许用户主动取消这一项。
- **D-10:** 发送前不做额外二次确认；预览区里展示的快照内容，就是实际发送给模型的上下文。

### 会话持久化、记忆裁剪与取消语义
- **D-11:** 对话记录不再只停留在前端内存，而是按 `documentId` 在后端持久化。
- **D-12:** 一个文档允许存在多个会话，一个会话下包含多条消息；重新打开同一文档时默认新建空会话，但用户可以手动打开旧会话。
- **D-13:** 当前活动线程在对话区默认展示最近 6 轮消息，旧会话通过会话入口切回查看，不要求默认把整条长线程全部摊开。
- **D-14:** 真正发送给模型的历史消息不按固定轮数硬裁，而是按模型上下文预算动态裁剪。
- **D-15:** 用户可以在上一条请求尚未结束时主动中断，并立即发起下一条请求。
- **D-16:** 点击“重试”前，需要让用户确认本次重试将使用的上下文，而不是静默复发。
- **D-17:** 当用户主动取消一条进行中的请求时，保留用户问题消息，并把原 assistant 占位改成“已取消”状态，而不是把这轮消息直接抹掉。
- **D-18:** 用户新建会话后，旧线程不能被直接删除；当前页面内应保留切回旧会话的入口。

### 抽屉交互与消息呈现
- **D-19:** 右侧抽屉采用“上方上下文区 + 中间消息列表 + 底部输入框”的主布局，不改成独立页面、弹窗或全新路由。
- **D-20:** 当前选区常驻显示在对话工作台中；章节目录单独放入次级 tab，而不是继续和选区并排堆成两个同级卡片。
- **D-21:** 会话入口放在消息区左上角的二级入口位置，便于在不离开当前抽屉的前提下切换旧线程。
- **D-22:** 发送中状态不在消息列表里预插 assistant 占位，也不额外插入加载行；只通过顶部状态条和发送按钮 loading 告知当前正在请求。
- **D-23:** 失败态只在线程内部显示失败消息卡和重试按钮，不再额外做顶部全局告警。
- **D-24:** 每条 assistant 消息下方默认展示简短的扩展信息摘要，至少包含 `usage`、`finishReason` 和 `providerResponseMeta` 中对用户有价值的内容。
- **D-25:** 右侧抽屉宽度扩展到当前实现的大约 2 倍，目标量级约为 `800px`，优先保证对话阅读、会话切换和上下文信息并存时仍有足够空间。

### 接口组织与工程边界
- **D-26:** LLM 接口不继续挂在 `/api/documents/{documentId}/...` 下，而是抽成独立的 `/api/llm/...` 路由，请求体里显式携带 `documentId`。
- **D-27:** 前端结构改为由 `EditorShell.vue` 挂载独立的 `EditorAiWorkbench.vue` 子组件；`EditorShell.vue` 继续负责编辑器壳层和 bridge，AI 工作台负责对话与会话管理。
- **D-28:** Phase 14 不只做“前端级取消”，后端也要维护可取消请求，并尽量向上游 provider 发起取消，避免长请求继续白跑。
- **D-29:** `llm.*` 配置根不仅持有 provider 配置，还要纳入启用开关、默认历史策略、超时与会话策略配置，避免这些策略散落回 `onlyoffice.integration.*`。
- **D-30:** 本 phase 的最低验证标准不是纯单测，而是至少要有一条接近真实链路的联调或 E2E 验证，覆盖“打开文档 -> 发起对话 -> 收到回复/取消/失败 -> 切换会话”中的关键路径。

### the agent's Discretion
- `providerResponseMeta` 的字段白名单边界、摘要展示文案和前端折叠形式由 research / plan 细化，但必须遵守“不透传原始响应”和“不暴露敏感信息”两条底线。
- 动态上下文预算的具体裁剪算法、会话列表排序字段以及取消请求的底层实现细节由 planner 自主确定。
- 会话入口的触发控件样式、目录 tab 的视觉排版和 `800px` 抽屉在窄屏场景下的降级方式可由后续实现决定。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone and phase scope
- `.planning/ROADMAP.md` — 定义 v1.1.0 的 phase 边界，以及 Phase 14 与 Phase 15 的职责分界。
- `.planning/REQUIREMENTS.md` — 当前需求映射，尤其是 `CHAT-01` 到 `CHAT-04` 与 `WRIT-01/02` 的边界。
- `.planning/PROJECT.md` — 产品目标、后端代理约束、当前里程碑背景和集成限制。

### Existing implementation conventions
- `.planning/codebase/CONVENTIONS.md` — 后端分层、Swagger/OpenAPI 注解、中文注释和前端模块拆分约定。
- `.planning/codebase/STRUCTURE.md` — 当前前后端目录结构和新增模块推荐落点。

### Phase 13 delivered baseline
- `.planning/phases/13-editor-runtime-bridge/13-01-SUMMARY.md` — bridge/plugin/bootstrap 背景。
- `.planning/phases/13-editor-runtime-bridge/13-02-SUMMARY.md` — 章节目录与跳转能力基线。
- `.planning/phases/13-editor-runtime-bridge/13-03-SUMMARY.md` — AI-ready 抽屉壳层与前端回归基线。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `packages/web/src/components/editor/EditorShell.vue`: 已经承载编辑器壳层、右侧抽屉、选区抓取、目录刷新、跳转和会话心跳，是 Phase 14 挂载 AI 子工作台的主入口。
- `packages/web/src/components/editor/onlyofficeBridge.js`: 已经打通宿主页与 ONLYOFFICE 插件协议，可继续提供选区和章节能力，不需要为了 AI 再造一套 bridge。
- `packages/web/src/lib/api.js`: 已统一封装访问上下文头，新的 `/api/llm/*` 请求仍应通过这里发送。
- `packages/web/public/onlyoffice-plugins/ai-bridge/code.js`: 已经定义选区抓取事件与桥接消息，是“明确快照”能力的源头。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java`: 当前 ONLYOFFICE 配置模型可作为拆分 `llm.*` 配置时的风格参考。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/OnlyofficeCommandServiceImpl.java`: 已示范如何基于 `RestClient.Builder` 调外部 HTTP 服务并做结果处理，可参考其调用和超时组织方式实现 LLM provider。

### Established Patterns
- 后端维持薄 controller + service 分层，并要求新增 model/DTO 带 Swagger/OpenAPI 注解。
- 前端宿主页已经把编辑器运行态职责集中在 `EditorShell.vue`，新增 AI 功能不应把状态倒灌回 `DocumentEditorPage.vue`。
- 访问上下文通过统一请求头解析，AI 接口即使迁到 `/api/llm/*` 也必须继续遵守当前 AccessContext 解析链路。
- 编辑页当前已经稳定承载 session heartbeat、close、save-status 等行为，AI 能力不能破坏这条主编辑链路。

### Integration Points
- `/api/llm/*` 需要同时接入 `documentId`、当前访问上下文和新的 `llm.*` 配置，而不是复用 `DocumentController` 里的 ONLYOFFICE 运行态职责。
- `EditorShell.vue` 需要演进为“编辑器壳层 + AI 子工作台”的组合关系，AI 对话与会话管理优先落在独立 `EditorAiWorkbench.vue` 子组件中。
- 文档维度会话持久化意味着后端需要新增会话/消息模型、列表读取接口以及取消请求状态管理。
- Phase 15 会复用 Phase 14 里的 assistant 消息模型做写回入口，因此消息记录里要保留稳定、可直接写回的主文本字段。

</code_context>

<specifics>
## Specific Ideas

- 右侧工作台不再停留在“AI-ready 准备态”，而是直接升级成正式对话工作台。
- 选区是默认上下文，但允许“空选区快照”明确参与请求，避免把“没抓到选区”与“系统忘了带上下文”混为一谈。
- 活跃章节标题默认自动带入本轮上下文，但必须给用户明显可见且可取消的提示。
- 会话持久化按文档维度进行，但重新打开文档时默认仍是干净的新会话，避免把旧上下文强塞给用户。
- 抽屉宽度扩大到约 `800px`，这是这次讨论明确提出的 UI 方向，不能再按当前约 `400px` 的窄侧栏心智设计消息区。
- 当前已有 `14-01/02/03-PLAN.md` 是基于旧上下文生成的；由于本次讨论已经重开并推翻多个关键假设，下游必须重跑 plan-phase，不能直接沿用旧计划执行。

</specifics>

<deferred>
## Deferred Ideas

- AI 回复写回当前光标或替换选区 — Phase 15
- 抽屉成为唯一默认右侧工作台后的最终 UI 收口与写回联动 — Phase 15
- 流式输出、多模型切换、prompt 模板治理、整篇文档/RAG 拼接 — 后续版本
- 跨文档共享或聚合会话、对话知识库化检索 — 超出当前 phase 范围

</deferred>

---

*Phase: 14-llm-conversation-chain*
*Context gathered: 2026-04-20*
