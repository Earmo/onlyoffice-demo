# Phase 3: User Context Integration - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段只负责把“真实用户上下文如何低耦合地进入文档服务”这件事做扎实。它要解决用户上下文的 SPI 抽象、内置 header/jwt provider、解析优先级、默认值和错误语义、用户身份如何接入 editor config / 文档 API / 轻量审计等问题，但不会在本阶段扩展成完整权限平台、完整审计中心、首页文档列表 UI 或分布式 callback 安全治理。

</domain>

<decisions>
## Implementation Decisions

### User Context Source Model
- 本阶段按 `SPI-first, built-ins included` 设计：核心边界是可插拔的用户上下文 provider，而不是把请求头或 JWT 写死成唯一来源。
- starter 内置 header provider 和 JWT provider 两种默认实现，但允许外部系统通过自定义 provider 覆盖或扩展。
- 用户上下文来源优先级必须可配置，不同接入方可以按需要调整 `header / jwt / custom provider` 的解析顺序。
- 当前代码中的 `RequestContextResolver` 更适合作为过渡实现，后续应演进为统一的访问上下文解析链或 provider 组合器。

### Failure and Default Semantics
- 如果请求完全没有提供任何用户上下文，默认不应静默放行；接口需要返回明确的 4xx 错误。
- 如果请求只缺少部分字段，可以按规则补齐默认值，但补齐能力必须同时受 `profile` 和显式开关控制。
- 默认用户上下文不能再被视为永远安全的兜底策略，只能作为受控 fallback。
- 一旦用户上下文解析失败，必须返回明确错误，而不是继续降级为默认用户或只打日志不阻断。

### Internal Access Model
- v1 的核心身份模型先保持最小闭环：`tenantId + sourceSystem + externalUserId + displayName`。
- 角色、权限、复杂组织身份等信息不进入 Phase 3 的核心上下文模型。
- 代码层推荐把当前的 `RequestContext` 演进为更清晰的 `AccessContext` 语义，统一表达租户、来源系统和当前操作者。
- 文档 `ownerUser` 不应继续简单等同于“当前请求用户”，Phase 3 开始明确区分“当前操作者”和“文档归属”。

### Permission and Audit Boundary
- Phase 3 可以透传和消费少量与编辑器直接相关的权限字段，但不做完整通用权限平台。
- `permissions map` 只覆盖最小编辑相关能力，如 edit/comment/download/print 这类直接影响 editor config 的字段。
- 本阶段允许补一张轻量审计事件表，用来记录 `documentId / actor / action / time / source` 这一类最小审计信息。
- callback 在本阶段应被视为 system event，不把它伪装成某个人类用户的直接保存动作。
- 如果上游系统要控制当前用户是否可编辑，优先通过轻量权限透传进入 editor config，而不是在本阶段直接落完整权限体系。

### Coverage in This Phase
- 真实用户上下文应尽量接入全部 API 路径，而不是只停留在 editor config。
- 本阶段至少要覆盖文档创建、上传、导入、列表、详情、editor-config 这些直接用户路径。
- callback 可以记录系统事件和相关上下文，但不要求在本阶段解决完整的人类会话归因问题。

### Claude's Discretion
- `AccessContext`、`UserContextProvider`、`UserContextResolverChain` 等具体命名由 planning / implementation 阶段细化。
- header provider 与 JWT provider 的配置项命名、claim/header 映射方式由 planning 阶段结合现有码决定。
- 轻量审计表的字段名称和持久化落点可在不突破本阶段边界的前提下由 planning 阶段裁量。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary
- `.planning/PROJECT.md` — 项目目标已经明确要求低耦合用户机制，避免与外部用户体系强耦合。
- `.planning/REQUIREMENTS.md` — `USER-01`、`USER-02`、`USER-03` 定义了本阶段必须满足的真实用户上下文、低耦合接入和业务消费要求。
- `.planning/ROADMAP.md` — `Phase 3: User Context Integration` 的目标、成功标准和三项计划是本阶段范围边界。
- `.planning/STATE.md` — 当前项目焦点已推进到 Phase 3，需要在 Phase 2 的 provider 路由与存储语义基础上继续扩展身份接入。

### Prior Phase Decisions
- `.planning/phases/01-service-foundation/01-CONTEXT.md` — 已锁定 headless-first、标准化上下文字段、服务到服务透传为主等关键约束。
- `.planning/phases/02-storage-strategy-layer/02-CONTEXT.md` — 已明确 `tenant/sourceSystem/documentId.ext`、异常文档可见性和 provider-neutral 设计，用户上下文接入不能破坏这些前提。
- `.planning/phases/02-storage-strategy-layer/02-03-SUMMARY.md` — 列表与详情已引入 `storageAvailable` 投影，Phase 3 需要在此基础上接入真实用户视角而不是推翻已有摘要语义。
- `.planning/phases/07-module-boundaries-and-repository-refactor/07-03-SUMMARY.md` — 对外命名和 starter 结构已稳定，用户上下文接入应继续沿用这套 naming baseline。

### Existing Runtime and Integration Behavior
- `docs/minimal-integration.md` — 当前已经说明服务到服务透传与标准请求头用法，是内置 header provider 的直接基线。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/RequestContextResolver.java` — 当前请求头解析入口，是 SPI 抽象的过渡起点。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/RequestContext.java` — 当前标准化上下文模型，是后续 `AccessContext` 演进的直接基础。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java` — 当前 editor config 已消费 `user.id` / `user.name`，是最直接的用户上下文接入点。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java` — 文档主数据 API 当前已经依赖 `RequestContextResolver`，后续需要让更多文档操作真正消费真实用户上下文。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RequestContextResolver` 已经集中承载请求头读取和默认值补齐，是演进为 provider 链的自然入口。
- `RequestContext` 已把租户、来源系统、外部用户、展示名收口成单一模型，为升级到 `AccessContext` 提供了直接切口。
- `OnlyofficeConfigService` 已经把 editor config 中的 `user.id` 和 `user.name` 集中组装，适合作为权限透传和用户身份接入的首个消费点。
- `DocumentApiController` 与 `DocumentController` 已经分离主数据 API 和运行时接口，便于分层推进身份接入和系统事件记录。

### Established Patterns
- 当前代码仍沿用“服务到服务透传 + 默认值回退”的思路，但缺少正式的 SPI 边界和严格错误语义。
- 文档归属字段目前还带有“当前用户即 owner”的惯性心智，Phase 3 需要开始把 owner 和 actor 语义拆开。
- starter 已具备较清晰的 config/service/web 分层，用户上下文接入最好继续以 resolver/provider/service 方式演进，而不是散到 controller 里。

### Integration Points
- 用户上下文 SPI 应优先替换或包裹 `RequestContextResolver`，避免上层 controller 继续直接感知“只支持请求头”。
- `OnlyofficeConfigService` 需要成为最小权限透传和用户身份注入 editor config 的核心出口。
- 文档创建、上传、导入、列表、详情等 API 需要逐步改为消费统一的访问上下文，而不是只拿默认用户。
- 如果引入轻量审计表，推荐沿用 Phase 7 之后的 `data/service` 模块边界，由 data 模块承载实体与 repository，service 模块承载记录时机。

</code_context>

<specifics>
## Specific Ideas

- 内置实现优先提供 header provider 和 JWT provider，同时允许业务方注册自己的 provider。
- 用户上下文解析顺序必须可配置，而不是在代码里永久写死。
- `permissions map` 只先承接和 editor config 直接相关的字段，不把完整权限系统压进 Phase 3。
- callback 先如实记录成 system event，避免在会话模型还没稳定前伪造“某个人保存了文档”。

</specifics>

<deferred>
## Deferred Ideas

- 完整权限模型、角色体系和更复杂的授权规则 —— 后续独立扩展
- 完整审计中心、差异日志和历史版本能力 —— 后续阶段或 v2
- 首页文档列表的用户视角 UI 呈现 —— Phase 4
- callback 的分布式可信性、安全校验和更完整会话归因 —— Phase 5

</deferred>

---

*Phase: 03-user-context-integration*
*Context gathered: 2026-03-23*
