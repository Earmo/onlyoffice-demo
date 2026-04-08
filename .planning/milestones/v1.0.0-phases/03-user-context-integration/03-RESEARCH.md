---
phase: 3
slug: user-context-integration
status: completed
created: 2026-03-23
sources:
  - .planning/phases/03-user-context-integration/03-CONTEXT.md
  - packages/server/onlyoffice-integration-service/pom.xml
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/RequestContextResolver.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/RequestContext.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
  - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentMetadataEntity.java
---

# Phase 3 Research

## Research Question

Phase 3 需要回答的核心问题不是“再多支持一种 token 解析方式”，而是“如何把当前仍偏请求头直读的用户上下文，升级为一个低耦合、可插拔、可在外部系统中长期复用的访问上下文边界，同时不把权限系统和完整审计平台一口气压进本阶段”。

## Current Baseline

- `RequestContextResolver` 已经能从 `X-Tenant-Id`、`X-Source-System`、`X-External-User-Id`、`X-User-Display-Name` 这四个请求头解析上下文，但它本质上还是“单实现 resolver”，不具备 SPI 或 provider 链能力。
- `RequestContext` 当前已经收口了最小身份模型：`tenantId / sourceSystem / externalUser / displayName`，并通过 `ownerUser()` 把当前外部用户直接映射为文档 owner。
- `OnlyofficeConfigService` 已经会把当前上下文中的 `user.id` / `user.name` 直接写入 ONLYOFFICE editor config，这是最直接的用户接入点。
- `DocumentApiController`、`DocumentController` 已经大量依赖 `RequestContextResolver`，说明 Phase 3 的重构切口应该在“解析层和上下文模型”，而不是先从 controller 拆起。
- service 模块已经带有 `jjwt` 依赖，这意味着 JWT provider 的最小实现基础已经存在，不需要再额外引入一整套认证框架。
- data 模块目前只有文档主数据，没有审计事件表；如果 Phase 3 要补轻量审计，应沿用 Phase 7 之后的数据层边界来落地。

## Recommended Technical Direction

### 1. Replace Single Resolver with an Access-Context Provider Chain

本阶段最稳的落点不是继续扩展 `RequestContextResolver` 的 `if/else`，而是引入一个 provider-neutral 的访问上下文解析链：

- 统一访问模型建议升级为 `AccessContext`
- 新增 `UserContextProvider` 或 `AccessContextProvider` SPI
- 内置 provider 至少包含：
  - `HeaderAccessContextProvider`
  - `JwtAccessContextProvider`
  - `DefaultAccessContextProvider`
- 再通过一个 `AccessContextResolverChain` 或同等聚合器统一调度

这样可以满足讨论阶段锁定的几件事：

- 核心抽象是 SPI，而不是 header 或 JWT
- 顺序可配置
- 业务方可自定义 provider 覆盖默认行为
- controller 不需要继续知道“当前只支持请求头”

### 2. Keep the Core Identity Model Minimal

讨论阶段已经锁定：本阶段不做完整权限系统，也不做复杂角色模型。因此核心上下文模型应保持最小闭环：

- `tenantId`
- `sourceSystem`
- `externalUserId`
- `displayName`
- 可选的最小 `permissions map`

不建议在本阶段把 `roles`、`org tree`、`scopes`、`session claims` 等信息塞进核心上下文。否则会迅速让 Phase 3 从“身份接入层”膨胀成“权限与组织平台”。

### 3. Separate Actor from Owner

当前 `RequestContext.ownerUser()` 的设计说明系统仍带有“当前调用者就是 owner”的惯性。讨论阶段已经明确要开始解耦：

- `current actor` 表示本次请求是谁在操作
- `ownerUser` 继续表示文档归属

这意味着 Phase 3 的实现不宜继续让 `DocumentMetadataService.createDocument(...)` 无条件用当前请求用户覆盖 owner。更稳的做法是：

- 在文档创建路径上，允许显式传入 owner；若没有，再按现有规则回退
- 同时把“当前 actor”单独用于 editor config、审计事件和运行时行为

即便本阶段不一次性重构 owner 规则，也至少要避免在新抽象里继续强化“owner = actor”。

### 4. Strict Errors for Missing Context, Controlled Fallback for Partial Gaps

讨论结果里最关键的一点是：完全缺失上下文时默认拒绝，但部分缺失时允许补齐。这要求设计上明确区分 3 类情况：

- **完全无上下文**
  - 返回 4xx
  - 禁止 silent fallback
- **部分缺失**
  - 可以按配置补齐
  - 是否允许补齐，受 `profile + explicit flag` 控制
- **解析失败**
  - 返回明确错误
  - 例如 claim 格式错误、header 值非法、JWT 无法解析

因此 Phase 3 很适合把配置模型扩成：

- `onlyoffice.integration.access-context.enabled-providers`
- `onlyoffice.integration.access-context.resolution-order`
- `onlyoffice.integration.access-context.allow-default-context`
- `onlyoffice.integration.access-context.require-explicit-context`
- `onlyoffice.integration.access-context.jwt.header-name`
- `onlyoffice.integration.access-context.jwt.claim-mappings.*`

### 5. Permissions Should Stay Close to Editor Config

本阶段允许引入少量 `permissions map`，但它的使用范围应被严格限制在“和 editor config 直接相关的最小能力”：

- `edit`
- `comment`
- `download`
- `print`

不建议本阶段就让这些权限大面积控制 service 层复杂分支，更不建议直接定义完整权限 DSL。最稳的方式是：

- 把最小 `permissions map` 放进 `AccessContext`
- `OnlyofficeConfigService` 消费这组字段并映射为 `document.permissions`
- controller / service 仅在明确需要的地方消费只读/可编辑语义

### 6. Lightweight Audit Event Table Is a Good Middle Ground

讨论阶段把审计边界收束到“轻量事件表”。这是一个很合适的折中，因为：

- 现有 `document_metadata` 只能表达“最后结果”，不能表达“谁在什么时候做了什么”
- 完整审计平台又明显超出本阶段范围

因此最合理的模型是新增一张最小事件表，例如：

- `event_id`
- `document_id`
- `tenant_id`
- `source_system`
- `actor_user`
- `actor_name`
- `event_type`
- `event_time`
- `event_source`
- `event_result`
- `message`

这足以覆盖：

- create/upload/import
- editor-config/open
- callback(system)

同时又不会强迫本阶段解决差异日志、版本回滚、完整链路追踪。

### 7. Treat Callback as System Event

这点在规划里必须写死：callback 不是“某个人类点击了保存”的直接事实。当前代码中的 callback 路径由 `DocumentController` 和 `DocumentStatusService` 驱动，因此最诚实的语义是：

- callback 事件本身记录为 `system`
- 如需关联最近会话用户，留给后续分布式会话模型

这能避免本阶段做出虚假的审计数据，也与 Phase 5 的会话/分布式保存状态更容易衔接。

## Domain Findings

### The Existing JJWT Dependency Lowers the Cost of a Built-In JWT Provider

`onlyoffice-integration-service` 已经有 `jjwt-api / impl / jackson` 依赖，因此实现内置 JWT provider 的成本主要在：

- 如何确定 token 来源（例如 `Authorization: Bearer ...`）
- claim 名映射如何配置
- 错误语义如何返回

这说明 Phase 3 完全可以把 JWT provider 作为官方内置实现之一，而不需要引入 Spring Security OAuth2 Resource Server 之类更重的体系。

### Resolver Order Must Be Configurable, Not Hard-Coded

因为讨论阶段明确选择了“优先级可配置”，所以本阶段规划不应把顺序写死成 `header > jwt > default` 或 `jwt > header > default`。更合理的做法是：

- 用配置声明顺序，例如 `header,jwt,default`
- 启动时按名称组装 provider 链
- 若链上都未解析成功，再根据 “是否允许 fallback” 决定报错还是补齐

### Access Context Touches Both API and Runtime Protocol

与 Phase 2 只改存储不同，Phase 3 既会影响：

- `DocumentApiController` 的创建、上传、导入、列表、详情
- `DocumentController` 的 editor-config

也就是说，执行时必须避免只改 editor config 而遗漏文档 API。否则 `USER-03` 很容易只完成一半。

### Audit Belongs in the Data Module, Trigger Logic Belongs in Service

沿用 Phase 7 后的模块边界，轻量审计表最合理的落点是：

- data 模块：entity / migration / repository
- service 模块：event recording service / timing / payload assembly

不建议把审计直接写进 controller；也不建议把审计事件粗暴塞回 `document_metadata`。

## Rejected or Deferred Options

### Keep Expanding `RequestContextResolver`

不建议。继续在一个 resolver 中同时处理 header、jwt、自定义扩展、fallback 和错误策略，很快会把用户接入层做成不可扩展的大型条件分支。

### Build Full Role/Permission System in Phase 3

不建议。讨论阶段已明确权限只做最小编辑相关透传，完整权限平台应该后置。

### Treat Callback as a Human User Action

不建议。当前还没有稳定的会话归因模型，直接把 callback 映射成人类用户会制造误导性的审计数据。

### Put Audit Inside `document_metadata`

不建议。主表适合表达“当前状态”，不适合承载多次动作的事件流。

## Implementation Implications for Planning

Phase 3 最稳的拆法仍然是 3 个 plan、3 个 wave：

1. 先建立 `AccessContext + Provider SPI + 配置模型 + 错误语义`
2. 再把访问上下文接进 editor config、文档 API 和最小 `permissions map`
3. 最后补轻量审计事件表、system callback 语义和外部系统扩展约定

这样拆的好处是：

- Wave 1 先把“怎么解析身份”做稳
- Wave 2 再把“身份如何影响当前业务行为”接通
- Wave 3 最后收口“如何被外部系统长期接入，以及怎样留下最小审计痕迹”

## Validation Architecture

本阶段验证建议继续以“resolver/provider 单测 + MVC 层行为测试 + data 层 repository 测试 + 文档检查”组合完成。

### Automated Focus

- `AccessContext` 配置与顺序绑定测试
- header provider / JWT provider / fallback provider 的解析测试
- 缺失上下文、部分缺失、解析失败时的 4xx 语义测试
- `OnlyofficeConfigService` 和 `DocumentController` 中 editor config 用户与最小 permissions 的映射测试
- `DocumentApiController` 中 create/upload/import/list/detail 对访问上下文的消费测试
- audit event repository 与 service 的最小写入测试

### Manual Focus

- `docs/minimal-integration.md` 是否新增 header/jwt/custom provider 三种接入说明
- starter 是否明确声明“完全无上下文默认报错、默认用户受 profile+开关控制”
- callback 审计是否被如实标记为 system event，而不是伪装成人类用户

### Recommended Commands

- 快速验证：`cd packages/server && mvn -q -DskipITs test`
- 完整验证：`cd packages/server && mvn test`
- 构建验证：`cd packages/server && mvn -q -DskipITs package`

## Planning Guardrails

- 不在本阶段实现完整角色/权限平台
- 不在本阶段实现完整审计中心或版本追踪
- 不把 callback 直接映射成人类保存者
- 不让 controller 继续直接依赖“固定请求头解析器”这一单一路径
- 不回退 Phase 2 已建立的 `storageAvailable`、provider-neutral 和 headless-first 语义

## Research Summary

最稳的路线是：

- 用 `AccessContext + Provider SPI` 取代当前单一 `RequestContextResolver`
- 内置 header / jwt / default 三类 provider，但顺序可配置、可被业务覆盖
- 严格区分“完全无上下文”“部分缺失”“解析失败”三种错误语义
- 让 editor config 与文档 API 都消费统一访问上下文，并只接入最小 `permissions map`
- 用轻量 audit_event 表补齐最小审计能力，同时把 callback 如实记录成 system event
