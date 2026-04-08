---
phase: 05-distributed-editing-flow
gathered: 2026-03-25
status: Ready for planning
---

# Phase 5: Distributed Editing Flow - Context

<domain>
## Phase Boundary

本阶段只负责把当前“能跑通”的 ONLYOFFICE 运行链路升级成“分布式部署下可信、可观测、可共享”的编辑运行层。重点是 callback 验签、保存状态共享持久化、运行时地址模型收口，以及远程资源导入/代理的安全边界。它不会在本阶段扩展成完整历史版本中心、复杂权限平台、活动后台或完整交付验证体系。

</domain>

<decisions>
## Implementation Decisions

### Callback Trust Boundary
- Phase 5 的 callback 主可信依据是 JWT 校验，而不是来源网络拓扑。
- 继续沿用当前 ONLYOFFICE JWT secret，把 callback 验签正式做完整，不额外引入 callback 专用 secret。
- Phase 5 暂时不把来源地址白名单做成硬门槛；网络边界可以作为补充，但不能成为唯一可信依据。
- callback 校验失败时必须返回明确 `4xx`，同时留下审计和运行状态痕迹，不能假装成功。

### Distributed Save Status Source of Truth
- `save-status` 不能继续停留在实例内存里，必须落到共享数据库，确保多实例下任意实例都能读到一致状态。
- 文档主表继续只保留摘要状态；详细保存状态需要独立的数据模型，而不是把运行态细节全部塞回主表。
- Phase 5 希望开始保留完整运行事件流，而不是只记最近一次结果，但这里的事件流仅服务于运行状态和排障，不等于历史版本系统。
- 前端 `save-status` 接口应返回“当前摘要状态 + 最近几条关键事件”，既能展示当前状态，也能解释最近发生了什么。

### Event Stream Scope Guardrail
- 运行事件流只记录关键运行态事件，例如 `editor_opened`、`callback_received`、`save_succeeded`、`save_failed`，不在本阶段扩展成完整编辑行为追踪中心。
- Phase 5 先不做事件归档、裁剪或生命周期治理，优先把正确性和一致性做出来。
- 列表页仍然只读主表摘要状态，不直接消费事件流表；事件流主要服务于编辑页保存状态和运行排障。

### Runtime URL Model
- 继续明确区分 `browser/public` 与 `onlyoffice/internal` 地址语义，不能回退到单一地址猜测。
- `document.url` 和 `callbackUrl` 都必须由后端按角色明确生成，前端和上游系统都不负责拼运行态 URL。
- 官方前端继续只消费 `documentId + editor-config`，不参与任何地址模式判断或运行时 URL 推导。
- `publicBaseUrl`、`internalBaseUrl`、`documentServerUrl` 等关键配置一旦不成立，应尽早失败并返回明确错误，而不是静默兜底成坏链接。

### Remote Resource Security
- 远程文档导入和图片代理统一纳入 SSRF 防护模型，不能分成两套不一致的安全边界。
- 文档导入和图片代理都要增加显式响应大小上限，避免异常大响应拖垮服务。
- 导入文档要同时校验扩展名与内容类型；图片代理要校验图片 media type，不能只信 URL 或响应头的单一信号。
- 当远程资源被安全策略拒绝时，返回明确 `4xx + 可读错误信息`，让调用方知道是策略拒绝而不是普通下载失败。

### Claude's Discretion
- callback JWT 的具体提取方式、签名字段和失败错误码，可在 planning 阶段结合当前 ONLYOFFICE integration 细化。
- 运行事件流表与当前摘要状态表是分表还是一表双职责，可在 planning 阶段按最小可交付方案拆解，但必须满足“共享数据库 + 多实例一致”。
- SSRF 防护的具体 CIDR 列表、大小上限默认值、媒体类型白名单可在 planning 阶段结合现有导入/图片代理代码细化。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary
- `.planning/PROJECT.md` — 项目核心价值明确要求服务可以分布式部署，并让文档编辑链路以低耦合方式被外部系统接入。
- `.planning/REQUIREMENTS.md` — `EDIT-01`、`EDIT-02`、`SAFE-01`、`SAFE-02`、`SAFE-03` 定义了本阶段必须解决的共享运行状态、分布式编辑闭环与安全边界。
- `.planning/ROADMAP.md` — `Phase 5: Distributed Editing Flow` 的目标、成功标准和三项计划是本阶段范围边界。
- `.planning/STATE.md` — 当前焦点已经切到 Phase 5，planning 需要基于已完成的存储、用户上下文和工作台入口继续推进分布式运行层。

### Prior Phase Decisions
- `.planning/phases/01-service-foundation/01-CONTEXT.md` — 已锁定 `publicBaseUrl`、`internalBaseUrl`、`documentServerUrl` 的地址分离模型。
- `.planning/phases/02-storage-strategy-layer/02-CONTEXT.md` — 已锁定共享存储、MinIO 正式基线、异常文档可见性以及 callback 失败保留旧版本的语义。
- `.planning/phases/03-user-context-integration/03-CONTEXT.md` — 已锁定 AccessContext SPI、最小 permissions map 和轻量审计语义。
- `.planning/phases/04-document-library-experience/04-CONTEXT.md` — 已锁定首页工作台、独立编辑页和列表-编辑器流转，本阶段不再回头扩 UI 为主的范围。

### Existing Runtime and Integration Behavior
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java` — 当前 editor-config、save-status、file、图片代理、callback 都从这里进入，是 Phase 5 的核心运行协议层。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java` — 当前负责生成 `document.url`、`callbackUrl` 和 `documentServerUrl`，是地址模型收口的关键位置。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java` — 当前负责文件读取、远程导入、callback 回写和图片代理相关下载，是共享存储与远程资源安全边界的关键位置。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStatusService.java` — 当前保存状态实现需要从实例内运行态升级到共享持久化模型。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeJwtService.java` — 当前 editor-config 已依赖 JWT 签名，Phase 5 需要把 callback 验签也接通进来。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- 当前后端已经拥有 `editor-config`、`file`、`callback`、`save-status` 这组运行态接口，不需要 Phase 5 重新设计协议入口。
- Phase 2 已经把文档文件读写切到统一存储策略，Phase 5 可以在此基础上继续收口共享运行态，而不是重新做文件层抽象。
- Phase 3 已经有轻量访问审计能力，callback 校验失败和运行态事件可以复用现有审计思路。
- Phase 4 已经把前端固定为“工作台首页 -> 独立编辑页”的流转，编辑页天然适合消费更丰富的 `save-status` 摘要和最近事件。

### Established Patterns
- 当前系统已经是 headless-first，运行时 URL 生成权应继续收敛在后端，不能扩散到前端或上游调用方。
- callback 失败时当前已经会写入运行状态并抛异常，这为 Phase 5 升级成共享持久化模型打下了行为基础。
- 远程资源导入与图片代理现在都走服务端 `RestClient`，因此 SSRF 防护和响应大小限制有自然的统一收口点。

### Integration Points
- `DocumentController.callback(...)` 是 callback JWT 验签、状态事件记录和共享回写的直接入口。
- `OnlyofficeConfigService.buildEditorConfig(...)` 是 `document.url` / `callbackUrl` / `documentServerUrl` 角色化生成的核心拼装点。
- `DocumentStatusService` 与 `DocumentSaveStatusResponse` 是编辑页轮询状态的现有落点，Phase 5 应在这里升级为共享数据库驱动的摘要 + 最近事件视图。
- `DocumentStorageService.importRemoteDocument(...)` 与 `OnlyofficeImageService` 是 SSRF、防大包、防伪装内容的主要治理点。

</code_context>

<specifics>
## Specific Ideas

- callback 可信性优先采用应用层 JWT 验签，把网络边界当作附加保护而非唯一真相源。
- 运行状态可以采用“事件流 + 当前摘要状态”的双层结构：事件表记录关键轨迹，摘要表或摘要投影支撑 `save-status` 当前视图。
- `save-status` 响应可以包含当前主状态、最近一次成功/失败时间、最近失败原因，以及最近几条关键事件，帮助编辑页解释当前状态。
- SSRF 防护建议统一覆盖远程文档导入和图片代理，至少阻止回环、本机、私网保留地址，并限制响应大小与媒体类型。

</specifics>

<deferred>
## Deferred Ideas

- 完整历史版本中心、版本差异和回放 —— 后续阶段或 v2
- 来源地址白名单的更复杂网络拓扑治理 —— 后续增强
- 完整事件归档、裁剪和生命周期策略 —— 后续阶段
- 更复杂的权限系统、协作者实时编辑策略 —— 后续阶段或 v2

</deferred>

---

*Phase: 05-distributed-editing-flow*
*Context gathered: 2026-03-25*
