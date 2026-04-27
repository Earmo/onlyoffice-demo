# Phase 14 LLM 工作台联调说明

这份文档收口 Phase 14 的配置键、验证分层、取消语义和 Phase 15 handoff 约束，避免后续联调再猜环境变量、线程字段或取消规则。

## 配置映射

> Phase 14.2 开始，主链路已经切到 `Spring AI + 独立 AI SSE`。当前只保留 `llm.default-provider / llm.default-model / llm.providers.*` 这一套多 provider 配置入口。

| 环境变量 | Spring 配置键 | 默认值 | 说明 |
|---|---|---|---|
| `LLM_ENABLED` | `llm.enabled` | `false` | 是否允许服务端真正发起模型调用 |
| `LLM_FEATURE_ENABLED` | `llm.feature-enabled` | `true` | 是否向前端暴露 AI 工作台能力 |
| `LLM_DEFAULT_PROVIDER` | `llm.default-provider` | `dashscope` | 默认运行时 provider |
| `LLM_DEFAULT_MODEL` | `llm.default-model` | provider 默认模型 | 默认运行时 model |
| `LLM_TIMEOUT_MILLIS` | `llm.timeout-millis` | `60000` | 上游请求超时时间 |
| `LLM_HISTORY_BUDGET_TOKENS` | `llm.history-budget-tokens` | `12000` | 历史窗口 token 预算 |

补充固定键：

- `LLM_REQUEST_SYNC_WAIT_MILLIS -> llm.request-sync-wait-millis`
- `LLM_HISTORY_TOKEN_ESTIMATOR -> llm.history-token-estimator`
- `LLM_ALLOW_HEADING_CONTEXT -> llm.allow-heading-context`
- `LLM_DEFAULT_SYSTEM_PROMPT -> llm.default-system-prompt`

DashScope 兼容模式对应的 Spring AI 配置：

- `spring.ai.model.chat=none`

推荐 provider 形态：

```yaml
llm:
  default-provider: dashscope
  default-model: qwen-plus
  providers:
    dashscope:
      label: DashScope
      spring-ai-provider: openai-compatible
      api-key: ${LLM_PROVIDER_DASHSCOPE_API_KEY}
      base-url: ${LLM_PROVIDER_DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      default-model: ${LLM_PROVIDER_DASHSCOPE_DEFAULT_MODEL:qwen-plus}
      models:
        - qwen-plus
        - qwen-max
```

## 验证分层

- 自动化验证：使用 fake provider 集成测试，覆盖成功、4xx、5xx、超时、取消后晚到成功等场景。
- 手工验证：使用真实 provider 做 smoke test，只验证端到端联调链路。
- CI 边界：真实 provider 不进入 CI，不要求在自动化环境中配置真实 `LLM_PROVIDER_*_API_KEY`。

当前分层原则：

- fake provider 负责固定 DTO、错误码、访问控制、流式事件、取消仲裁和晚到结果丢弃。
- 真实 provider 只负责确认联通性、实际提示词效果和模型侧限流/风控差异。

## 自动化范围

后端自动化已覆盖：

- `GET /api/llm/capability`
- `POST /api/llm/sessions`
- `POST /api/llm/messages/stream`
- `POST /api/llm/messages`
- `GET /api/llm/requests/{requestId}`
- `POST /api/llm/requests/{requestId}/cancel`
- 不同 `tenantId` / `actorUser` 访问旧会话拒绝
- `chars_div_4` 历史预算估算
- `providerResponseMeta` 白名单过滤
- `reasoning-delta` 实时推理增量和终态 `providerResponseMeta.reasoningContent`
- `errorCode` 映射
- `cancelled` 晚到成功结果丢弃

前端自动化已覆盖：

- capability disabled
- stale response 忽略
- stream started / delta / completed
- `reasoning-delta`、深度思考 Markdown 展示、失败/取消 partial 保留
- 断流后单次最终态回查
- `in_progress -> cancelled`
- `LLM_SESSION_NOT_FOUND` / `LLM_SESSION_FORBIDDEN` 回退新会话
- 错误卡片展示 `errorCode`
- 编辑页路由离开前先 `closeEditingSession`

## 真实 Provider Smoke Test

联调前准备：

1. 设置 `LLM_ENABLED=true`
2. 设置 `LLM_FEATURE_ENABLED=true`
3. 提供目标 provider 的 `LLM_PROVIDER_*_BASE_URL`（如有自定义地址）
4. 提供目标 provider 的 `LLM_PROVIDER_*_API_KEY`
5. 提供目标 provider 的 `LLM_PROVIDER_*_DEFAULT_MODEL`
6. 启动服务端和前端，进入 `/editor/{documentId}`

至少执行下面 7 条 smoke test：

1. 带选区发送
   先在文档中选中文本，再发送问题，确认回复与当前选区相关。
2. 无选区发送
   不选择文本直接发送，确认请求体仍带 `selectionSnapshot.emptySelection=true`，线程可正常返回结果。
3. 失败后确认重试
   触发一次 provider 失败，确认线程卡片展示 `errorCode`，点击重试后会先展示待复用上下文，再由用户确认发送。
4. 请求中取消
   让请求进入 `in_progress` 后点击取消，确认顶部状态变为已取消，线程最终停在 `cancelled`。
5. 流中断后回查终态
   发送请求后主动断开浏览器网络或中断当前页面请求，确认前端只自动回查一次 `GET /api/llm/requests/{requestId}`，并在服务端已完成时正确显示最终结果。
5. 重新打开文档默认新空会话但可切回旧线程
   关闭并重新进入同一文档，确认会创建新的空会话，同时旧线程仍可在会话列表切回查看。
6. Network 面板无 `apiKey` / `Authorization`
   浏览器开发者工具里检查 `/api/llm/*` 请求，确认前端请求体和响应体都不出现 `apiKey`、上游 `Authorization` 或原始 provider header。
7. 浏览器 console / 错误 toast / 服务端 debug log 示例中无敏感字段
   检查 `console`、错误提示和服务端 debug log，确认不存在 `LLM_PROVIDER_*_API_KEY`、原始上游请求体或 `Authorization` 值。

建议额外确认：

- 快速连续切换文档时，旧文档的 stale response 不会污染当前线程。
- capability disabled 时，输入区和空态卡片同时退化。

## 取消语义

Phase 14 的取消规则固定如下：

- 本地取消优先。
- 上游取消是 best effort。
- 用户一旦取消，请求最终状态必须保持 `cancelled`。
- provider 随后晚到成功时，不得覆盖本地 `cancelled` 状态。
- 取消前已经进入本地 accumulator 的正文和 `reasoningContent` 可以随 `cancelled` 终态保留。
- 取消登记之后的晚到 provider chunk 不得继续回写 `assistantText` 或 reasoning。

这条规则同时适用于：

- `/api/llm/requests/{requestId}/cancel`
- 前端流式会话中的终态合并
- 服务端执行注册表对进行中请求的仲裁

## 安全边界

浏览器永远只消费本项目 DTO，不直接接触 provider 秘钥或原始上游报文。

明确禁止透传到前端的内容：

- `apiKey`
- `Authorization`
- 原始 provider request body
- 原始 response headers
- 完整 raw payload
- `system_fingerprint`

允许出现在 `providerResponseMeta` 的字段只保留白名单：

- `provider`
- `model`
- `created`
- `usage.promptTokens`
- `usage.completionTokens`
- `usage.totalTokens`
- `reasoningContent`

## Phase 16 extension: 深度思考流式协议

Phase 16 在 Phase 14.2 的独立 AI SSE 基础上新增了与 `assistant-delta` 并列的 `reasoning-delta` 事件。后端收到 provider reasoning 增量时立即向浏览器发送，不等待 `assistant-meta` 或 `assistant-completed`。

示例：

```text
event: reasoning-delta
data: {"requestId":"...","reasoningText":"..."}
```

字段语义：

- `reasoningText` 是本次 SSE frame 的推理增量片段。
- `assistant-delta.data.delta` 仍只表示正文回复增量。
- `providerResponseMeta.reasoningContent` 仍是 terminal event、历史消息和断流回查中的完整聚合推理内容。
- 如果同一个 provider chunk 同时包含 reasoning 和正文，服务端先发送 `reasoning-delta`，再发送 `assistant-delta`。

前端展示规则：

- 深度思考块展示在 assistant 正文之前，进行中、完成态和历史消息顺序一致。
- 深度思考块默认折叠；生成中标题为“深度思考中”，终态标题为“深度思考”。
- 用户展开后看到 Markdown 渲染内容；Markdown 输出必须经过 DOMPurify 清洗后才能进入 `v-html`。
- 失败或取消时，前端保留已经收到的 `reasoningText` 和正文片段；如果 terminal payload 没有非空 `reasoningContent`，不得用空值覆盖已收到的 streamed reasoning。

## Phase 15 Handoff

Phase 15 写回能力必须直接复用现有线程字段，不重新发起模型请求。

固定 handoff 接点：

- `assistantText`
- `assistantMessageId`
- `sessionId`

写回阶段的约束：

- 从当前线程中选择已有 assistant 消息写回文档。
- 写回只消费现有 `assistantText`，不为了“重新生成”而再次调用 `/api/llm/messages`。
- 写回记录应能回溯到 `assistantMessageId` 和 `sessionId`。

## 当前验证结论

- 自动化主链路已经基于 fake provider 固定，不依赖真实模型服务。
- 真实 provider 仅保留为手工 smoke test 入口。
- Phase 15 可以直接站在 `assistantText`、`assistantMessageId`、`sessionId` 这 3 个字段上接写回闭环。

## 代码步骤导读

### 服务端主流程

1. `LlmController` 接收 `/api/llm/*` 请求，并从 `AccessContextResolver` 解析当前 `tenantId` / `actorUser`。
2. `LlmConversationService.streamMessage()` / `sendMessage()` 先校验 provider/model，再通过 `LlmConversationAccessGuard` 确认当前用户确实能访问该 `documentId + sessionId`。
3. 服务端先落库 `user message`，再预插 `assistant message(status=pending)`，最后创建 `document_llm_request(status=in_progress)`。
4. `LlmPromptWindowBuilder` 根据 `historyBudgetTokens` 和 `chars_div_4` 规则裁剪历史窗口，始终保留 system prompt、当前问题和当前快照。
5. `SpringAiProviderRegistry` 解析逻辑 provider，`OpenAiCompatibleSpringAiLlmProvider` 通过 Spring AI OpenAI model 发起流式对话。
   DashScope 与 SiliconFlow 都通过 OpenAI-compatible 接口归一到同一实现。
6. 流式增量只写到 `text/event-stream`，正式 `assistantText` 只在 terminal path 一次性落库。
7. provider 返回后，`LlmRequestExecutionRegistry` 先仲裁终态所有权：
   - 若取消先赢，晚到成功直接丢弃
   - 若完成先赢，取消不能再反向覆盖 completed
8. 服务端将终态写回 `document_llm_request` 和 `document_llm_message`，前端只在流中断后用 `GET /api/llm/requests/{requestId}` 做一次最终态回查。

### 前端主流程

1. `EditorShell` 只维护编辑器生命周期、bridge 和右侧工作台显隐，把聚合后的 `runtimeContext` 传给 `EditorAiWorkbench`。
2. `EditorAiWorkbench` 首先请求 `getLlmCapability(documentId)`，决定当前是 `capability-enabled` 还是 `capability-disabled`。
3. capability 可用后，工作台自动 `createLlmSession(documentId)` 建立新空线程，并拉取最近会话列表。
4. 工作台从 capability 读取 `defaultProvider/defaultModel/availableProviders`，允许用户在发送前切换 provider/model。
5. 用户发送问题时，工作台会把当前 `selectionSnapshot`、`headingContext`、`provider`、`model` 和 `retryConfirmed` 一起发给后端，并直接打开 `POST /api/llm/messages/stream`。
6. `assistant-delta` 只更新当前 pending 条目的临时显示态；`assistant-completed / assistant-cancelled / assistant-error` 负责终态收口。
7. 如果流异常结束但没有明确 terminal event，前端只调用一次 `getLlmRequest(requestId, documentId)` 做最终态确认。
8. 所有异步响应都要经过 `documentId/sessionId/requestId` stale guard，不匹配就丢弃，避免切文档或切线程后串线。

### 关键代码入口

- 服务端配置入口：`packages/server/onlyoffice-integration-service/src/main/resources/application.yml`
- 服务端主服务：`packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationService.java`
- OpenAI-compatible provider 适配：`packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/OpenAiCompatibleSpringAiLlmProvider.java`
- 终态仲裁：`packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmRequestExecutionRegistry.java`
- 前端工作台：`packages/web/src/components/editor/EditorAiWorkbench.vue`
- 前端 API 封装：`packages/web/src/components/editor/editorAiApi.js`
- 前端 AI SSE helper：`packages/web/src/components/editor/llmMessageStream.js`

## Phase 14.2 阅读顺序

建议按下面顺序读代码，能最快把链路串起来：

1. `application.yml`
   先确认逻辑 provider、默认模型、`spring-ai-provider` 映射和 base URL。
2. `LlmConversationService`
   看 `beginRequest()`、`streamMessage()`、`executeProviderStream()`，理解预落库、流式执行和终态收口。
3. `LlmRequestExecutionRegistry`
   看 completed / cancelled / failed 的仲裁规则，理解为什么晚到成功不会覆盖 cancelled。
4. `OpenAiCompatibleSpringAiLlmProvider`
   看 DashScope / SiliconFlow 如何归一化成 `SpringAiProviderChunk`。
5. `EditorAiWorkbench.vue`
   看 `loadCapabilityAndBootstrap()`、`sendCurrentQuestion()`、`reconcileRequestOnce()`，理解前端如何接流和防串线。
6. `llmMessageStream.js`
   最后看浏览器侧 POST SSE 的拆帧逻辑，确认 terminal event 和断流补偿是怎么进入工作台的。

## Phase 14.2 调试步骤

1. 先看浏览器 Network 里的 `POST /api/llm/messages/stream`
   确认响应类型是 `text/event-stream`，并且第一帧是 `request-started`。
2. 再看服务端日志里的 `requestId`
   用同一个 `requestId` 串起 `document_llm_request`、`document_llm_message` 和 provider 错误日志。
3. 如果前端停在 `in_progress`
   先确认是否收到了 `assistant-completed / assistant-error / assistant-cancelled`，没有的话再看 `reconcileRequestOnce()` 是否成功回查。
4. 如果 provider 返回 404/4xx
   先检查对应 provider 的 `base-url` 和 `spring-ai-provider` 映射是否正确，再检查模型名是否在 allowlist 中。
5. 如果切文档后出现串线
   直接检查 `isBootstrapStale()`、`isSessionLoadStale()` 和 `isCurrentRequestTarget()` 三个 guard 是否命中。
