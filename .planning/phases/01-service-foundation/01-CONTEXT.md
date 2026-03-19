# Phase 1: Service Foundation - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段只定义这个项目作为独立文档服务和微服务时的服务边界、共享主模型和基础配置结构。它要明确服务如何被调用、文档主数据如何被表达、前后端与 ONLYOFFICE 的地址关系如何划分，但不会在本阶段展开 MinIO 具体实现、用户适配器具体代码实现、首页文档列表 UI 细节或分布式编辑流程重构。

</domain>

<decisions>
## Implementation Decisions

### Service Shape
- 项目按 `headless-first, UI-available` 设计：以后端服务为主，官方前端是该服务的一等客户端，但不是唯一接入方式。
- v1 同时支持三种交付视角：`api service`、`official web`、`compose demo`。
- 官方前端不是唯一产品边界；纯 API 集成必须是正式支持路径，而不是附带能力。

### Document Ownership Model
- 文档服务维护自己的主文档模型，而不是完全依赖上游业务系统的文档主数据。
- 每份文档由服务内部自生成稳定的 `documentId` 作为主键。
- v1 一份文档只绑定一个 `sourceSystem + externalDocumentId` 组合；后续如需多绑定再扩展映射表。
- 主模型中 `tenantId` 必填、`ownerUserId` 必填，确保所有文档都有明确租户和 owner 归属。
- `title` 和 `storageKey` 必须分离：显示标题可变，存储对象键保持稳定。
- `sourceSystem` 必填；即使本服务内部创建的文档，也使用明确来源值（如 `native`）。

### Document Status Model
- 主文档状态在 v1 直接包含 `draft / editing / saved / failed / archived`。
- 文档列表页直接展示主状态，不把状态隐藏到详情页之后。
- 允许后续在分布式编辑阶段补充更细的编辑会话或回调状态模型，但不推翻这张主文档表。

### External Integration Contract
- 上游系统优先通过后端 API 与文档服务建立文档上下文，再由文档服务返回内部 `documentId`。
- `open` 和 `create/import` 是分离动作；默认不允许“打开时隐式 auto-create”。
- 用户上下文接入同时支持多种方式，但以上游显式透传标准化 user context 为主。
- v1 认证方式按“服务到服务认证 + 标准化上下文透传”定义，文档服务不自建一套强耦合登录体系。
- 文档服务应预留 `tenant / sourceSystem / externalUser / externalDocument` 这些跨系统上下文字段。

### Deployment and Address Model
- 默认支持前后端分离部署，同时保留通过网关或 nginx 聚合为统一入口的能力。
- 配置必须显式区分 `publicBaseUrl` 和 `internalBaseUrl`，不能再依赖单一 demo 地址模型覆盖所有场景。
- 官方前端只消费 `documentId` 和后端返回的 session/config；真实编辑地址、文件地址和 callback 地址都由后端生成。
- 当前仓库中的一体化 compose 形态保留为本地联调与演示产物，而不是唯一部署形态。

### Claude's Discretion
- Phase 1 内具体采用数据库还是其他共享元数据持久化载体，由后续 research / planning 根据现有码和部署目标细化。
- `sourceSystem`、`externalDocumentId`、`tenantId`、`ownerUserId` 等字段的最终命名风格可在保持语义不变的前提下由规划阶段微调。
- 官方前端、API service、compose demo 三类产物的仓库目录组织可由规划阶段决定。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary
- `.planning/PROJECT.md` — 项目已经从“ONLYOFFICE 集成 demo”重定向为“分布式文档服务”，并定义了低耦合用户接入、可插拔存储和前后端分离等非协商约束。
- `.planning/REQUIREMENTS.md` — `ARCH-01`、`ARCH-02`、`ARCH-03` 定义了本阶段必须覆盖的服务化、微服务接入与分布式基础要求。
- `.planning/ROADMAP.md` — `Phase 1: Service Foundation` 的目标、成功标准和三项计划是本阶段的直接范围边界。
- `.planning/STATE.md` — 当前项目焦点和最近一次 scope reset 信息，避免沿用旧的 demo-hardening 假设。

### Current Baseline and Existing Behavior
- `README.md` — 当前仓库对外仍被描述为最小集成示例，后续规划需要明确从 demo 到正式服务的迁移落差。
- `docs/minimal-integration.md` — 现有同源入口、容器内回调路径和关键 API 的基线行为说明，后续不能在不了解这些约束的情况下重构地址模型。
- `.planning/codebase/ARCHITECTURE.md` — 当前系统仍以单体 Spring Boot + Vue + nginx + 本地文件系统为基础，规划阶段需要识别哪些边界可复用、哪些必须拆开。
- `.planning/codebase/CONCERNS.md` — 已明确指出单机文件存储、内存保存状态、默认密钥和 callback 安全等问题，这些是本阶段建模的重要输入。

### Existing Runtime Configuration
- `packages/server/src/main/resources/application.yml` — 当前 demo 配置入口，后续需要从这里演进出更清晰的 public/internal 地址与服务化配置模型。
- `docker-compose.yml` — 当前 demo 的三容器一体化部署方式，是 `compose demo` 产物的现状基线。
- `packages/web/nginx.conf` — 当前统一入口与 ONLYOFFICE 路径聚合方式，是“网关聚合能力需要保留”的直接依据。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/DemoProperties.java`：已经有统一配置根，适合演进为服务化配置模型，而不是再散落到多个硬编码位置。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeConfigService.java`：已经把 ONLYOFFICE editor config 生成集中在一个服务里，适合作为后续“只返回 documentId，由后端生成真实地址”的核心边界。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/model/StoredDocument.java`：虽然当前仍偏文件视角，但可作为过渡期参考，拆解出更正式的文档主模型字段。
- `packages/web/nginx.conf`：当前同域聚合入口已经验证过 ONLYOFFICE 路径代理可行，可作为“保留网关聚合能力”的基础资产。

### Established Patterns
- 后端以 `controller -> service -> model/config` 的 Spring 分层为主，新的服务边界、主模型和配置模型应继续沿用这个分层，而不是把契约散进控制器。
- 当前配置已经隐含 `documentServerUrl` 与 `internalBaseUrl` 的双地址意识，只是表达还偏 demo；这为后续显式的 `public/internal` 地址模型提供了现成切口。
- 当前文档存储和文档标识强依赖 `DocumentStorageService` 的本地文件路径推导，这是 Phase 1 必须先切开的脆弱边界之一。

### Integration Points
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java` 是未来文档服务 API 边界的直接入口，后续计划应从这里延展出 list/open/create/import 等服务化接口。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStorageService.java` 是当前最接近持久化层的实现，后续应被“文档主模型 + 存储策略 + 元数据层”拆分吸收。
- `packages/web/src/App.vue` 当前直接围绕固定文档编辑流程构建，后续官方前端可以重用其编辑集成经验，但不能继续把它当最终服务边界。

</code_context>

<specifics>
## Specific Ideas

- 服务定位明确按“独立后端服务优先，但保留官方前端”来设计。
- 官方前端后续应该只是这套服务的一个正式客户端，不应决定服务内核的契约长相。
- 文档主模型应该能表达“这是本服务自己的文档”，同时也能表达“它对应外部系统的某个业务文档”。
- v1 产物明确分成 `api service / official web / compose demo` 三类，这一点对后续目录、脚本和部署文档有直接影响。

</specifics>

<deferred>
## Deferred Ideas

- MinIO 的具体存储策略实现和 bucket/object 设计 —— Phase 2
- 外部用户上下文适配器的具体输入格式、请求头或 token claim 细节 —— Phase 3
- 首页文档列表的页面结构、筛选方式和上传交互 —— Phase 4
- ONLYOFFICE 在分布式部署下的 callback、安全和共享保存状态闭环 —— Phase 5

</deferred>

---

*Phase: 01-service-foundation*
*Context gathered: 2026-03-19*
