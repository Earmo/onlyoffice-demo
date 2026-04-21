# Phase 14 LLM 工作台联调说明

这份文档收口 Phase 14 的配置键、验证分层、取消语义和 Phase 15 handoff 约束，避免后续联调再猜环境变量、线程字段或取消规则。

## 配置映射

| 环境变量 | Spring 配置键 | 默认值 | 说明 |
|---|---|---|---|
| `LLM_ENABLED` | `llm.enabled` | `false` | 是否允许服务端真正发起模型调用 |
| `LLM_FEATURE_ENABLED` | `llm.feature-enabled` | `true` | 是否向前端暴露 AI 工作台能力 |
| `LLM_PROVIDER` | `llm.provider` | `openai-compatible` | 当前 provider 策略名 |
| `LLM_BASE_URL` | `llm.base-url` | 空 | 上游模型服务 base URL |
| `LLM_API_KEY` | `llm.api-key` | 空 | 上游模型 API Key，只允许保留在服务端 |
| `LLM_MODEL` | `llm.model` | 空 | 默认模型名 |
| `LLM_TIMEOUT_MILLIS` | `llm.timeout-millis` | `60000` | 上游请求超时时间 |
| `LLM_HISTORY_BUDGET_TOKENS` | `llm.history-budget-tokens` | `12000` | 历史窗口 token 预算 |

补充固定键：

- `LLM_REQUEST_SYNC_WAIT_MILLIS -> llm.request-sync-wait-millis`
- `LLM_HISTORY_TOKEN_ESTIMATOR -> llm.history-token-estimator`
- `LLM_ALLOW_HEADING_CONTEXT -> llm.allow-heading-context`
- `LLM_DEFAULT_SYSTEM_PROMPT -> llm.default-system-prompt`

## 验证分层

- 自动化验证：使用 fake provider 集成测试，覆盖成功、4xx、5xx、超时、取消后晚到成功等场景。
- 手工验证：使用真实 provider 做 smoke test，只验证端到端联调链路。
- CI 边界：真实 provider 不进入 CI，不要求在自动化环境中配置真实 `LLM_API_KEY`。

当前分层原则：

- fake provider 负责固定 DTO、错误码、访问控制、轮询、取消仲裁和晚到结果丢弃。
- 真实 provider 只负责确认联通性、实际提示词效果和模型侧限流/风控差异。

## 自动化范围

后端自动化已覆盖：

- `GET /api/llm/capability`
- `POST /api/llm/sessions`
- `POST /api/llm/messages`
- `GET /api/llm/requests/{requestId}`
- `POST /api/llm/requests/{requestId}/cancel`
- 不同 `tenantId` / `actorUser` 访问旧会话拒绝
- `chars_div_4` 历史预算估算
- `providerResponseMeta` 白名单过滤
- `errorCode` 映射
- `cancelled` 晚到成功结果丢弃

前端自动化已覆盖：

- capability disabled
- stale response 忽略
- `in_progress -> completed`
- `in_progress -> cancelled`
- `LLM_SESSION_NOT_FOUND` / `LLM_SESSION_FORBIDDEN` 回退新会话
- 错误卡片展示 `errorCode`
- 编辑页路由离开前先 `closeEditingSession`

## 真实 Provider Smoke Test

联调前准备：

1. 设置 `LLM_ENABLED=true`
2. 设置 `LLM_FEATURE_ENABLED=true`
3. 提供 `LLM_BASE_URL`
4. 提供 `LLM_API_KEY`
5. 提供 `LLM_MODEL`
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
5. 重新打开文档默认新空会话但可切回旧线程
   关闭并重新进入同一文档，确认会创建新的空会话，同时旧线程仍可在会话列表切回查看。
6. Network 面板无 `apiKey` / `Authorization`
   浏览器开发者工具里检查 `/api/llm/*` 请求，确认前端请求体和响应体都不出现 `apiKey`、上游 `Authorization` 或原始 provider header。
7. 浏览器 console / 错误 toast / 服务端 debug log 示例中无敏感字段
   检查 `console`、错误提示和服务端 debug log，确认不存在 `LLM_API_KEY`、原始上游请求体或 `Authorization` 值。

建议额外确认：

- 快速连续切换文档时，旧文档的 stale response 不会污染当前线程。
- capability disabled 时，输入区和空态卡片同时退化。

## 取消语义

Phase 14 的取消规则固定如下：

- 本地取消优先。
- 上游取消是 best effort。
- 用户一旦取消，请求最终状态必须保持 `cancelled`。
- provider 随后晚到成功时，不得覆盖本地 `cancelled` 状态。
- 晚到成功结果不得回写 `assistantText`。

这条规则同时适用于：

- `/api/llm/requests/{requestId}/cancel`
- 前端轮询中的终态合并
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

- `model`
- `created`
- `usage.promptTokens`
- `usage.completionTokens`
- `usage.totalTokens`

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
2. `LlmConversationService.sendMessage()` 先校验 capability，再通过 `LlmConversationAccessGuard` 确认当前用户确实能访问该 `documentId + sessionId`。
3. 服务端先落库 `user message`，再预插 `assistant message(status=pending)`，最后创建 `document_llm_request(status=in_progress)`。
4. `LlmPromptWindowBuilder` 根据 `historyBudgetTokens` 和 `chars_div_4` 规则裁剪历史窗口，始终保留 system prompt、当前问题和当前快照。
5. `OpenAiCompatibleLlmProviderStrategy` 把 prompt window 归一化成 openai-compatible `messages`，调用 `/chat/completions`。
6. provider 返回后，`LlmRequestExecutionRegistry` 先仲裁终态所有权：
   - 若取消先赢，晚到成功直接丢弃
   - 若完成先赢，取消不能再反向覆盖 completed
7. 服务端将终态写回 `document_llm_request` 和 `document_llm_message`，前端通过 `GET /api/llm/requests/{requestId}` 轮询读取稳定 DTO。

### 前端主流程

1. `EditorShell` 只维护编辑器生命周期、bridge 和右侧工作台显隐，把聚合后的 `runtimeContext` 传给 `EditorAiWorkbench`。
2. `EditorAiWorkbench` 首先请求 `getLlmCapability(documentId)`，决定当前是 `capability-enabled` 还是 `capability-disabled`。
3. capability 可用后，工作台自动 `createLlmSession(documentId)` 建立新空线程，并拉取最近会话列表。
4. 用户发送问题时，工作台会把当前 `selectionSnapshot`、`headingContext` 和 `retryConfirmed` 一起发给后端。
5. 若后端在 `requestSyncWaitMillis` 内没完成，前端把请求切到 `in_progress`，每 1500ms 调一次 `getLlmRequest(requestId, documentId)`。
6. 所有异步响应都要经过 `documentId/sessionId/requestId` 三重 stale guard，不匹配就丢弃，避免切文档或切线程后串线。
7. 如果发送失败或取消失败，工作台会把错误固化成线程卡片和顶部错误状态，而不是让 pending 条目永远挂住。

### 关键代码入口

- 服务端配置入口：`packages/server/onlyoffice-integration-service/src/main/resources/application.yml`
- 服务端主服务：`packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmConversationService.java`
- provider 适配：`packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/OpenAiCompatibleLlmProviderStrategy.java`
- 终态仲裁：`packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/llm/LlmRequestExecutionRegistry.java`
- 前端工作台：`packages/web/src/components/editor/EditorAiWorkbench.vue`
- 前端 API 封装：`packages/web/src/components/editor/editorAiApi.js`
