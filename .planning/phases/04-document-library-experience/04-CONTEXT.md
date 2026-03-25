---
phase: 04-document-library-experience
gathered: 2026-03-25
status: Ready for planning
---

# Phase 4: Document Library Experience - Context

<domain>
## Phase Boundary

本阶段只负责把首页从“打开即进入固定文档编辑器”改造成“文档工作台入口”，让用户先看到文档列表，再决定新建、上传、导入或进入某个文档编辑。它要解决首页的信息结构、创建入口、列表交互、编辑页入口、空状态和异常状态，但不会在本阶段扩展成完整活动中心、复杂文档检索平台、分布式 callback 治理或完整权限系统。

</domain>

<decisions>
## Implementation Decisions

### Home Information Architecture
- 首页不是纯列表页，而是轻量工作台首页：顶部保留轻量信息卡和主操作区，主区域仍由文档列表主导。
- 首屏列表除基础文档信息外，要直接暴露异常提示，特别是 `storageAvailable=false` 这类系统状态不能被藏起来。
- 默认排序按最近保存或最近操作时间倒序，优先帮助用户继续最近处理过的文档。
- 顶部应轻量展示当前租户和当前用户上下文，让 Phase 3 的访问上下文能力在前端有明确落点，但不能喧宾夺主。

### Navigation and Editor Entry
- `/` 作为文档工作台首页，`/editor/:id` 作为独立编辑工作台路由。
- 编辑页仍应保留工作台感，可以有侧边切换或返回入口，但页面主语义已经是“正在编辑某个文档”。
- 从一个文档切换到另一个文档时，需要显式确认，避免 ONLYOFFICE 嵌入式编辑器在未确认的情况下误切文档。
- 编辑页始终需要明确的“返回文档列表”入口，不能只依赖浏览器返回。

### Create and Import Entry Shape
- 首页顶部主操作区并列突出三类入口：新建空白文档、上传本地文档、导入远程文档。
- 新建和上传要作为并列主动作，远程导入也保持可见，不埋进过深的次级菜单。
- 新建、上传、导入成功后，先回到列表并高亮新结果，由用户明确选择进入编辑器，而不是强制自动跳转。
- 这一阶段的重点是把工作台入口做清楚，而不是把所有创建动作直接绑定成“创建即编辑”。

### Empty and Error States
- 当当前租户下没有任何文档时，空状态要强调“新建或上传第一个文档”，而不是只显示冷冰冰的暂无数据。
- 如果文档元数据存在但 `storageAvailable=false`，仍显示在主列表中，并以明确异常标签展示。
- 列表加载失败时，采用列表区域内错误态加重试按钮，不把整个首页打成报错页。
- `failed`、只读、来源异常等状态要直接通过状态标签体现在列表项中，而不是隐藏到详情页之后。

### Workbench Scope Guardrail
- 工作台感先控制在“轻量信息卡 + 主列表 + 主操作区”，不做重型后台首页。
- “最近活动”在 Phase 4 里不做独立活动流，只做基于现有列表数据的轻量“最近文档”区。
- 搜索可以直接走后端搜索参数；筛选允许做多维筛选，但它们必须服务于文档进入流程，不能演变成完整文档检索系统。
- 搜索/筛选无结果时，先保持克制，只显示明确无结果态，不额外把它做成营销式空态。
- 列表项支持整行点击进入文档，但删除、重试等危险动作必须单独隔离，避免误触。

### Claude's Discretion
- 轻量信息卡的具体内容、视觉权重和布局比例由 planning / implementation 阶段结合现有单页前端决定。
- 最近文档区和主列表是否共享同一数据源、同一组件可由 planning 阶段裁量，但不能额外引入重型活动流模型。
- 搜索和多维筛选的最小字段集合可在 planning 阶段结合现有文档摘要字段细化。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary
- `.planning/PROJECT.md` — 项目目标已经明确首页需要先展示文档列表，再进入编辑流程。
- `.planning/REQUIREMENTS.md` — `LIB-01`、`LIB-02`、`LIB-03` 定义了本阶段必须满足的列表入口、选择进入和上传后进入流程。
- `.planning/ROADMAP.md` — `Phase 4: Document Library Experience` 的目标、成功标准和三项计划是本阶段范围边界。
- `.planning/STATE.md` — 当前焦点已经推进到 Phase 4，planning 需要基于已完成的存储和用户上下文能力继续推进前端入口体验。

### Prior Phase Decisions
- `.planning/phases/01-service-foundation/01-CONTEXT.md` — 已锁定 headless-first、前后端分离和 `/api/documents` 作为文档主数据入口。
- `.planning/phases/02-storage-strategy-layer/02-CONTEXT.md` — 已锁定 `storageAvailable` 语义和 provider-neutral 存储边界，列表页不能把异常文档静默隐藏。
- `.planning/phases/03-user-context-integration/03-CONTEXT.md` — 已锁定访问上下文、actor 语义和轻量权限接入，首页顶部和编辑入口需要沿用这套上下文能力。
- `.planning/phases/03-user-context-integration/03-03-SUMMARY.md` — 轻量审计和 callback system event 已经建立，Phase 4 不需要重做这些后端边界。

### Existing Runtime and Integration Behavior
- `packages/web/src/App.vue` — 当前前端单页默认直接加载 `demo` 文档，是 Phase 4 需要重构的直接入口。
- `packages/web/src/style.css` — 当前前端样式基线，后续首页工作台与编辑页布局需要在现有视觉基础上演进。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java` — 已提供列表、详情、创建、上传、远程导入接口，是首页工作台的直接后端基础。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentSummaryResponse.java` — 已包含 `status`、`storageAvailable`、`actorUser`、`lastSavedTime` 等字段，可直接支撑 Phase 4 的列表信息设计。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java` — 已提供 `editor-config` 和 callback 入口，编辑页需要继续围绕这条链路工作。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- 后端的 `/api/documents`、`/api/documents/upload`、`/api/documents/import-remote` 已经具备工作台首页所需的大部分主路径能力。
- `DocumentSummaryResponse` 已有状态、异常可见性和最近保存时间，不需要 Phase 4 从零重新定义列表字段。
- 当前 `App.vue` 已有上传、远程导入、切换只读、重载 editor-config 等动作实现，可拆解成工作台首页与编辑页两个界面继续复用。
- ONLYOFFICE 编辑器配置仍由后端统一生成，前端不需要在 Phase 4 引入额外协议拼装。

### Established Patterns
- Phase 3 已经让 controller 和 service 统一消费 `AccessContext`，前端 Phase 4 应继续把当前用户/租户上下文当作既有能力，而不是重新设计身份入口。
- 现有前端还是单文件单页模式，Phase 4 大概率需要开始引入更清晰的页面状态或路由分层。
- 当前控制台式界面已经存在上传和导入动作，但它们还属于编辑器附属操作，Phase 4 要把这些动作提升为首页主入口。

### Integration Points
- 首页工作台直接消费 `DocumentApiController.list()` 返回的 `DocumentListResponse`。
- 编辑页继续通过 `/api/documents/{id}/editor-config` 获取 ONLYOFFICE 配置。
- 新建、上传、导入成功后需要回流到列表状态管理，支持高亮新增结果。
- 如果要做后端搜索和多维筛选，应优先扩展现有列表接口，而不是新增一套完全独立的数据源。

</code_context>

<specifics>
## Specific Ideas

- 首页顶部可以用 1-2 个轻量信息卡承接“当前租户/当前用户”和最近文档摘要。
- 最近文档区可以直接从当前列表数据投影，不必单独引入活动流 API。
- 搜索建议优先按标题和关键元数据走后端查询；筛选可围绕状态、异常、只读、来源等现有字段展开。
- 列表项建议整行可进入编辑器，但行内危险动作必须和主点击区做明显隔离。

</specifics>

<deferred>
## Deferred Ideas

- 独立活动流、审计流首页可视化 —— 后续阶段或 v2
- 完整文档搜索系统、复杂多条件检索和高级筛选 —— 后续扩展
- callback 分布式安全治理、保存一致性和更复杂状态流 —— Phase 5
- 更完整的权限平台、共享协作者与细粒度授权 —— 后续阶段或 v2

</deferred>

---

*Phase: 04-document-library-experience*
*Context gathered: 2026-03-25*
