---
phase: 06-verification-and-delivery
gathered: 2026-03-26
status: Ready for planning
---

# Phase 6: Verification and Delivery - Context

<domain>
## Phase Boundary

本阶段只负责把当前已经完成的服务能力收口成“可验证、可交付、可接手”的项目基线。重点是补齐后端高风险链路回归测试、建立前端页面级自动化测试、统一仓库验证入口，以及把独立部署/微服务接入/配置矩阵/验收清单整理成正式交付文档。它不会在本阶段继续扩展新的业务能力、重做前后端架构、引入重型多实例自动化平台，或把文档工作变成完整运维平台工程。

</domain>

<decisions>
## Implementation Decisions

### Backend Test Coverage Boundary
- Phase 6 的后端测试优先围绕高风险链路补强，而不是按模块做机械式全量铺开。
- 自动化重点放在 callback JWT、共享运行状态、远程资源安全边界、ONLYOFFICE 运行时地址模型、provider 路由等安全与分布式链路。
- 测试粒度以 `service + controller` 为主，repository 只维持必要覆盖，不把本阶段变成持久化层重测工程。
- 对多存储/provider 行为，不只覆盖当前默认 provider，还应顺手把 provider 扩展点的测试骨架铺好，为后续 COS / OSS 预留稳定回归位。
- “真正多实例同时运行”的一致性仍然保留手动验证；自动化先覆盖摘要语义、共享状态读写和关键状态流转。

### Frontend Test Shape
- 前端自动化优先采用 `Vitest + Vue Test Utils`，建立稳定的页面/组件级回归基线。
- 工作台首页与编辑页两边都要覆盖，但重点是核心状态流转和导航语义，而不是平均铺开所有细节。
- ONLYOFFICE 嵌入本身在前端测试里以 mock `editor-config`、mock API 和页面状态渲染为主，不追真实 iframe 或脚本集成。
- 前端测试目标是防止 Phase 4 的列表入口、创建入口、编辑页返回/切换/状态区等核心流程回退，而不是在本阶段构建完整端到端验收平台。

### Unified Verification Entry
- 仓库需要提供根级统一验证入口，不能继续只靠“知道的人自己拼命令”。
- 统一入口应按任务分层，例如后端测试、前端测试、组合验证、交付校验等，而不是只提供一个难以定位问题的 all-in-one 命令。
- `docker compose config` 继续作为默认自动化编排校验入口；Phase 6 不把 `compose up` 级联调脚本强行纳入自动化主路径。
- 交付文档和 README 需要给出推荐执行顺序，让新接手的人知道应先装依赖、再跑哪些测试、最后做哪些编排校验。

### Delivery Documentation Boundary
- Phase 6 的文档交付面允许比“最小说明”更完整，但仍要围绕三条主线收口：独立部署、微服务接入、验证清单。
- 配置说明可以按场景形成更完整的配置矩阵，而不只是零散示例；但它仍然是交付配置表，不扩成重型运维手册。
- 需要补一份简洁的人工验收/交付验证清单，用来承接自动化之外的联调和交付检查。
- 文档落点可以升级成更清晰的 `docs` 结构，而不是继续把所有内容堆进单一文件；同时保留根 README 作为总入口。

### Scope Guardrail
- Phase 6 可以升级文档结构、统一验证命令、补前后端自动化测试，但不要顺手引入新的业务功能、复杂 CI 平台或大规模容器级联调框架。
- 前端测试以工作台和编辑页状态为主，不追真实 ONLYOFFICE 嵌入执行。
- 后端测试以当前高风险服务链路为主，不在本阶段重新设计持久化模型或外部 provider 正式实现。
- 交付文档强调“按文档可完成部署和验证”，不追求一次做成完整 SRE/运维知识库。

### Claude's Discretion
- 根级统一验证入口的具体实现可以是 `package.json` 脚本、PowerShell 脚本、Maven/npm wrapper 或组合方案，由 planning 阶段结合现有仓库结构裁量。
- 前端测试文件的落点、mock 策略和页面拆分粒度可由 planning 阶段结合当前 Vue 结构细化，但必须优先覆盖工作台首页和编辑页核心流转。
- 文档结构可以在 `README.md`、`docs/minimal-integration.md` 之外新增更明确的交付文档，但要避免无节制扩文件数量。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary
- `.planning/PROJECT.md` — 项目目标已经明确要求服务既能独立部署，也能作为微服务低耦合集成，Phase 6 的交付文档必须围绕这两种形态收口。
- `.planning/REQUIREMENTS.md` — `QUAL-01`、`QUAL-02`、`QUAL-03` 定义了本阶段必须完成的后端自动化测试、前端自动化测试和统一交付验证入口。
- `.planning/ROADMAP.md` — `Phase 6: Verification and Delivery` 的目标、成功标准和三项计划是本阶段的范围边界。
- `.planning/STATE.md` — 当前焦点已经切到 Phase 6，下一步 planning 必须以“验证与交付收口”为主，而不是继续扩业务功能。

### Prior Phase Decisions
- `.planning/phases/04-document-library-experience/04-CONTEXT.md` — 已锁定工作台首页、独立编辑页、创建回流列表和状态展示语义，是前端测试的核心基线。
- `.planning/phases/05-distributed-editing-flow/05-CONTEXT.md` — 已锁定 callback JWT、共享运行状态、角色化运行时 URL、SSRF 与远程资源安全边界，是后端高风险测试主轴。
- `.planning/phases/05-distributed-editing-flow/05-VERIFICATION.md` — 已记录当前阶段的自动化与手动验证路径，Phase 6 要在此基础上提升为更稳定的交付基线。
- `.planning/phases/07-module-boundaries-and-repository-refactor/07-03-SUMMARY.md` — 后端多模块骨架已经稳定，新增测试和验证入口应沿用当前 `data/service` 模块边界。

### Existing Runtime and Delivery Behavior
- `packages/server/pom.xml` — 当前后端统一测试入口仍是 `mvn test`，Phase 6 需要继续把多模块测试基线收口好。
- `packages/web/package.json` — 当前前端只有 `dev/build/preview` 脚本，还没有正式测试命令，是 Phase 6 需要补齐的直接入口。
- `README.md` — 当前 README 仍偏快速启动入口，尚未形成完整验证顺序和交付入口说明。
- `docs/minimal-integration.md` — 当前已有最小接入说明，但还没有形成更清晰的独立部署/微服务接入/交付验证分层结构。
- `.planning/phases/05-distributed-editing-flow/05-VALIDATION.md` — 当前已经有 Phase 5 的验证合同，可作为 Phase 6 统一验证入口和交付清单的基线样式。

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- 后端已经有较完整的 `Spring Boot Test + MockMvc + service` 测试基线，像 `DocumentControllerTest`、`DocumentApiControllerTest`、`OnlyofficeConfigServiceTest`、`DocumentStorageServiceTest`、`OnlyofficeImageServiceTest` 等都可直接扩展。
- Phase 5 已经把高风险链路集中到少数关键服务里：`DocumentStatusService`、`OnlyofficeJwtService`、`OnlyofficeConfigService`、`RemoteResourceSecurityService`、`DocumentStorageService`、`OnlyofficeImageService`。
- 前端已经清晰拆成工作台首页、编辑页和编辑器壳层，页面测试可以直接围绕 `DocumentLibraryPage.vue`、`DocumentEditorPage.vue`、`EditorShell.vue` 建立。
- 当前 `docker compose config`、`mvn test -q`、`corepack pnpm build` 已经是可工作的阶段级验证命令，可作为统一验证入口的起点。

### Established Patterns
- 每个 phase 都已经有 `VALIDATION.md` 和 `VERIFICATION.md`，说明项目已经形成“自动化验证 + 手动验证路径 + 执行结果回写”的流程习惯。
- 后端测试更偏 controller/service 行为验证，而不是 repository 深挖，Phase 6 应继续沿这个高收益模式补强。
- 官方前端仍然是 ONLYOFFICE 运行时配置的消费者，不应该在前端测试里追真实 editor 嵌入，mock 页面状态才是更稳定的方向。
- 当前交付文档入口分散在 `README.md` 和 `docs/minimal-integration.md`，Phase 6 需要把它们升级成更清晰的“入口页 + 分场景文档”结构。

### Integration Points
- 后端测试主线应围绕 `DocumentApiController`、`DocumentController`、`OnlyofficeConfigService`、`OnlyofficeJwtService`、`DocumentStatusService`、`DocumentStorageService`、`OnlyofficeImageService` 扩展。
- 前端测试主线应围绕 `/` 工作台首页、`/editor/:id` 编辑页、创建入口回流、高亮、返回列表、切换确认、保存状态区渲染扩展。
- 根级统一验证入口需要编排 `packages/server`、`packages/web` 和根目录 `docker compose config`，成为交付验证的统一入口。
- 文档交付面应至少衔接根 README、最小接入说明和新增交付说明，形成一致的执行顺序与配置矩阵。

</code_context>

<specifics>
## Specific Ideas

- 后端可以优先补 callback JWT 拒绝、save-status 最近事件投影、provider 路由分发、远程资源安全拒绝等回归测试。
- 前端测试建议以 `Vitest + Vue Test Utils` 为主，先覆盖工作台首页加载、创建动作回流列表、编辑页返回列表、切换确认和保存状态渲染。
- 根级验证入口可以设计成分层命令，例如“后端测试、前端测试、组合验证、交付检查”，同时保留子模块原生命令。
- 文档可以拆成“快速入口 README + 最小接入说明 + 验证/交付文档 + 配置矩阵”，但仍保持结构克制。

</specifics>

<deferred>
## Deferred Ideas

- 真实多实例容器自动化测试平台 —— 后续增强
- Playwright 级完整端到端 ONLYOFFICE 嵌入验收 —— 后续增强
- 完整运维手册、监控告警体系和 SRE 手册 —— 后续阶段或 v2
- COS / OSS 正式实现与对应 provider 的完整验证套件 —— 后续阶段

</deferred>

---

*Phase: 06-verification-and-delivery*
*Context gathered: 2026-03-26*
