# Phase 2: Storage Strategy Layer - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段只负责建立统一存储抽象，并以 MinIO 跑通首个正式可用策略，为后续接入腾讯云 COS 和阿里云 OSS 预留稳定扩展面。它要解决“文档文件本体如何统一读写、上传/导入后的内容如何落到对象存储、ONLYOFFICE callback 如何回写最新版本、local 如何作为开发过渡策略保留”这些问题，但不会在本阶段展开用户体系接入、首页文档列表 UI、插图资源治理或 callback 安全校验。

</domain>

<decisions>
## Implementation Decisions

### Strategy Positioning
- MinIO 是 v1 的正式默认策略，本地文件系统只保留为开发与过渡期兼容策略。
- 存储策略选择需要保留按 `tenant` 或 `sourceSystem` 扩展的方向，不能把 provider 分发永久写死成单一全局实现。
- 默认行为按 profile 区分：开发/测试可回退 local，正式部署以 MinIO 为主。
- 当前 local 策略不再被视为长期主路径，只作为平滑迁移和本地开发便利性保留。

### Storage Scope
- Phase 2 的统一存储抽象先覆盖：文档文件本体读取、上传/导入后的最终写入、ONLYOFFICE callback 回写。
- 默认引导文档或空白文档内容仍由 service 层生成，再通过存储策略写入，不把“生成文档内容”塞进 provider 内部。
- 远程导入的网络下载行为仍放在 service 层，存储策略只负责最终持久化写入。
- 插图资源在本阶段不纳入正式存储范围，只为后续扩展预留接口空间。

### Consistency Semantics
- 如果数据库中存在文档元数据，但底层对象不存在，文档仍显示在列表中，但应标记为异常/失败状态，打开时返回明确错误。
- 创建、上传、导入等“首次建档”流程应尽量追求原子性：对象写失败时尽量回滚，不留下半成品元数据记录。
- callback 回写失败属于“已有文档的新版本写入失败”，应保留最近成功版本，文档仍可打开，但主状态标记为 `failed`。
- 后续列表页默认展示异常文档，并给出明确的异常状态和提示，而不是静默隐藏。

### MinIO Object Organization
- v1 采用单 bucket + key 前缀分层，而不是按租户或来源分多个 bucket。
- `storageKey` 结构优先采用 `tenant/sourceSystem/documentId.ext`，把租户、来源和文档稳定身份体现在 key 中。
- 对象 key 不保留原始文件名，展示名仍由元数据中的 `title` 承担，保持对象键稳定。
- key 设计从一开始就按 provider-neutral 语义组织，避免出现 MinIO 专属概念，便于后续平滑扩 COS / OSS。

### Claude's Discretion
- local 与 MinIO 两种策略在代码中的接口命名、包结构和 Bean 装配方式由规划阶段决定。
- MinIO 连接配置项的拆分粒度（endpoint、bucket、accessKey、secretKey、path-style 等）由 research / planning 阶段细化。
- 若需要在 callback 回写前增加临时对象写入、覆盖写入或简单版本保护，可由实现阶段在不改变上面产品语义的前提下自行裁量。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary
- `.planning/PROJECT.md` — 项目从 demo 升级为可分布式部署的文档服务，明确了存储策略化和微服务接入是核心目标。
- `.planning/REQUIREMENTS.md` — `STOR-01`、`STOR-02`、`STOR-03` 定义了本阶段必须覆盖的统一存储抽象、MinIO 首个实现和 COS/OSS 扩展面。
- `.planning/ROADMAP.md` — `Phase 2: Storage Strategy Layer` 的目标、成功标准和三项计划是本阶段的范围边界。
- `.planning/STATE.md` — 当前项目焦点已经切回 Phase 2，需要在 Phase 7 已完成的新模块结构上继续推进。

### Prior Phase Decisions
- `.planning/phases/01-service-foundation/01-CONTEXT.md` — 已锁定 `title` 与 `storageKey` 分离、文档主数据进数据库、服务走 headless-first，这些是存储抽象的前提。
- `.planning/phases/07-module-boundaries-and-repository-refactor/07-01-SUMMARY.md` — 后端已拆成 `data/service` 双模块，Phase 2 的存储抽象应落在新的模块边界内。
- `.planning/phases/07-module-boundaries-and-repository-refactor/07-02-SUMMARY.md` — 数据层已具备 repository 与新字段命名规范，存储策略需要与现有元数据模型协同工作。
- `.planning/phases/07-module-boundaries-and-repository-refactor/07-03-SUMMARY.md` — 对外命名已统一为 `onlyoffice-integration-starter`，存储配置与文档说明必须延续这套命名。

### Existing Runtime and Integration Behavior
- `docs/minimal-integration.md` — 当前对外 API、editor-config、file、callback 的运行边界说明，规划阶段需要保证存储切换后这些路径语义不变。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java` — 现有本地文件系统实现基线，后续需要从这里抽出统一存储接口。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java` — 文档元数据与状态流转真相源，存储策略必须围绕现有元数据主模型协同。
- `packages/server/onlyoffice-integration-service/src/main/resources/application.yml` — 当前 starter 配置根和 `storage-root` 基线，是扩展存储 provider 配置的直接入口。
- `docker-compose.yml` — 当前 compose 联调入口，后续 MinIO 本地联调需要与这套部署方式兼容。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DocumentStorageService` 已经集中承载创建、上传、导入、读取、callback 回写逻辑，是抽取 `StorageStrategy` 的最直接切口。
- `DocumentMetadataService` 已经把 `storageKey`、状态流转、主数据创建等动作收口，后续可继续作为“对象写入前后更新元数据”的协调层。
- `OnlyofficeIntegrationProperties` 已有 `storageRoot` 配置，可扩展为更正式的 provider/profile/storage 配置模型。
- `DocumentController` 和 `DocumentApiController` 已经分离主数据 API 与 ONLYOFFICE 运行时接口，存储重构不需要重定义外部 HTTP 边界。

### Established Patterns
- service 层负责业务流程和异常语义，data 层负责持久化；存储 provider 最好与现有分层保持一致，不要把业务判断塞回 controller。
- 当前 callback 状态已经写回数据库主状态模型，这为“保留旧版本 + 标 failed”语义提供了直接落点。
- 现有码已使用 `storageKey` 而不是直接拿文件名当身份，说明 key 结构可以继续演进而不需要改变主模型。

### Integration Points
- Phase 2 的新抽象应替换 `DocumentStorageService` 中的本地 `Path/Files` 细节，但尽量不改变 `DocumentController`、`OnlyofficeConfigService`、`DocumentApiController` 的对外调用方式。
- MinIO 策略需要和 compose、本地 profile、测试 profile 一起考虑，而不是只实现一个单独的 SDK 封装类。
- 由于 Phase 7 已把项目切成多模块，数据库相关模型和 repository 继续留在 data 模块，存储 provider 和业务协调逻辑优先落在 service 模块。

</code_context>

<specifics>
## Specific Ideas

- local 策略可以继续保留给开发和测试，但整体心智要切到“MinIO 才是正式对象存储基线”。
- callback 回写失败和创建/导入失败的语义要明确区分：前者保留旧版本，后者尽量回滚。
- MinIO key 先采用 `tenant/sourceSystem/documentId.ext` 这种中性结构，不要把 provider 特性写进 key 语义。

</specifics>

<deferred>
## Deferred Ideas

- 真实用户上下文如何进入对象 key 或访问控制 —— Phase 3
- 首页文档列表如何展示异常文档与存储状态 —— Phase 4
- callback 验签、远程导入更严格的 SSRF/大小/安全治理 —— Phase 5
- 插图资源是否并入统一对象存储 —— 后续单独扩展

</deferred>

---

*Phase: 02-storage-strategy-layer*
*Context gathered: 2026-03-23*
