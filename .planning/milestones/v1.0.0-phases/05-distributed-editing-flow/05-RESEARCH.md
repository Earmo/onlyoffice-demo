---
phase: 5
slug: distributed-editing-flow
status: completed
created: 2026-03-25
sources:
  - .planning/phases/05-distributed-editing-flow/05-CONTEXT.md
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStatusService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeImageService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeJwtService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentSaveStatusResponse.java
---

# Phase 5 Research

## Research Question

Phase 5 需要回答的核心问题不是“再补几个回调判断”，而是“如何把当前已经能工作的 ONLYOFFICE 运行链路，升级成分布式部署下可信、可共享、可观测的运行时协议层，同时不把这一阶段膨胀成完整版本中心或复杂安全平台”。

## Current Baseline

- `DocumentController` 已经统一承载 `editor-config`、`save-status`、`file`、图片代理和 `callback`，说明运行态协议入口已经集中，不需要重新发明第二套接口。
- `OnlyofficeConfigService` 已经负责拼 `document.url`、`callbackUrl` 和 `documentServerUrl`，但当前仍主要依赖配置与 request 推导，没有把“配置缺失时尽早失败”正式做成硬约束。
- `DocumentStatusService` 当前只是 `DocumentMetadataService` 的轻包装，保存状态仍然直接写在文档主表字段上，还没有“事件流 + 当前摘要状态”的独立运行态模型。
- `OnlyofficeJwtService` 当前只负责签名，尚未承担 callback 验签职责，但现有 `jjwt` 基础已经足够扩成验签入口。
- `DocumentStorageService` 与 `OnlyofficeImageService` 当前已经有最小的 URL 校验和 `localhost` 阻断，但还没有统一的 SSRF、防大包、内容类型校验和拒绝语义模型。
- 测试基线已经存在 `DocumentControllerTest`、`DocumentStatusServiceTest`、`OnlyofficeConfigServiceTest`、`DocumentStorageServiceTest`、`OnlyofficeImageServiceTest`，说明 Phase 5 可以建立在现有单测 / MVC 测试入口上推进。

## Recommended Technical Direction

### 1. Split Runtime State Into Summary + Event Stream

讨论阶段已经锁定：主表只保留摘要状态，详细保存状态单独建表。因此最稳的方向是：

- 文档主表继续保存当前 `status`、最近成功保存时间等摘要字段
- 新增一张“编辑运行事件”表，记录关键事件：
  - `editor_opened`
  - `callback_received`
  - `save_succeeded`
  - `save_failed`
- `DocumentStatusService` 升级成“共享数据库驱动的运行态门面”，而不是继续只代理 `DocumentMetadataService`

这样既满足多实例一致，也不会一步膨胀成完整历史版本中心。

### 2. Make Callback JWT the Primary Trust Mechanism

当前 editor-config 已经有签名能力，因此 callback 最自然的可信边界就是复用同一套 JWT secret 做验签。实现重点在：

- callback token 从哪里取
- 如何对 payload 做验签
- 验签失败时如何返回明确 `4xx`
- 验签失败时如何记录审计和运行状态

讨论阶段已经明确不把来源白名单作为 Phase 5 的强制门槛，因此规划应把网络来源限制视为后续增强，而不是本阶段主路径。

### 3. Treat Runtime URLs as Explicit Role-Based Outputs

Phase 1 已锁定 `publicBaseUrl / internalBaseUrl / documentServerUrl` 分离模型，Phase 5 的关键是把这套模型从“有配置”推进到“有约束”：

- `document.url` 和 `callbackUrl` 必须统一由后端按角色生成
- 官方前端继续只拿 `documentId + editor-config`
- 如果关键地址配置不成立，就尽早失败，而不是继续 request 推导或静默容错

这能把分布式部署正确性从“猜对 URL”转成“配置正确才允许进入运行态”。

### 4. Unify Remote Import and Image Proxy Hardening

`DocumentStorageService.importRemoteDocument(...)` 和 `OnlyofficeImageService.proxyRemoteImage(...)` 当前都通过服务端 `RestClient` 发起外部请求，因此它们非常适合统一收口安全策略：

- 回环和私网地址阻断
- 显式响应大小上限
- 文档导入的扩展名 + 内容类型双校验
- 图片代理的图片媒体类型校验
- 被策略拒绝时返回明确 `4xx + 可读错误`

不建议让导入和图片代理各自长出一套不一致的安全模型。

### 5. Keep the Phase Focused on Runtime Trust, Not Full Collaboration History

讨论阶段额外压实了一个重要边界：事件流只服务“运行态和排障”，不是完整历史中心。因此：

- 不在 Phase 5 做版本差异或回放
- 不在 Phase 5 做事件归档和生命周期治理
- 不让列表页直接依赖事件流表
- `save-status` 只返回“当前摘要状态 + 最近几条关键事件”

这能保证 Phase 5 聚焦分布式运行可信性，而不是把问题做散。

## Domain Findings

### The Current `DocumentStatusService` Is Structurally Too Thin for Multi-Instance Runtime State

现在的 `DocumentStatusService` 只是把几个状态调用继续委托给 `DocumentMetadataService`。这说明 Phase 5 真正的结构变化点很明确：

- 不能只在 service 层加几个 if
- 必须补数据模型、repository 和响应投影

否则它依然只是“主表字段的读写包装器”，无法承接讨论阶段锁定的完整事件流。

### Callback Trust and URL Generation Are Coupled Through `DocumentController`

`DocumentController` 同时负责 callback、file 下载和 editor-config 请求前的状态初始化，这意味着 Phase 5 的 callback 验签、状态更新和错误语义最终都要在这里闭环。计划如果把 callback 校验和运行状态拆得过散，执行时会很容易交叉打架。

### Existing Tests Allow a Focused Backend-Heavy Phase

Phase 5 基本不改前端主结构，更多是运行时后端治理，因此验证主轴应继续是：

- MVC 测试：`DocumentControllerTest`
- service 单测：`DocumentStatusServiceTest`、`OnlyofficeConfigServiceTest`
- 远程资源单测：`DocumentStorageServiceTest`、`OnlyofficeImageServiceTest`
- 全量回归：`mvn test`

不需要为这次 planning 假设新的前端测试基建。

## Rejected or Deferred Options

### Rely on Source Network Checks as the Main Callback Trust Boundary

不建议。讨论阶段已经明确 JWT 是主校验机制，来源网络最多只是补充，不应成为 Phase 5 的真相源。

### Keep Runtime State Only in `document_metadata`

不建议。这样虽然实现快，但会让运行态轨迹和主表摘要耦死，无法满足“最近几条关键事件 + 多实例一致”的目标。

### Let Frontend Participate in Runtime URL Resolution

不建议。当前系统已经是 headless-first，前端继续只消费 `documentId + editor-config` 更符合前面阶段锁定的边界。

### Add Full History/Archive Strategy in the Same Phase

不建议。Phase 5 只做关键运行事件，不顺手扩成版本中心或事件归档平台。

## Implementation Implications for Planning

Phase 5 最稳的拆法仍然是 3 个 plan、3 个 wave：

1. 先建立共享运行状态模型，把 `save-status` 从主表直读升级为“摘要 + 事件流”
2. 再把 callback JWT、运行时 URL 校验和 editor/file/callback 闭环一起收口
3. 最后补远程导入与图片代理的统一安全边界和接入文档

这样拆的好处是：

- Wave 1 先解决“状态在多实例下是否可信”
- Wave 2 再解决“ONLYOFFICE 运行时协议是否可信”
- Wave 3 最后解决“远程资源入口是否可信”

## Validation Architecture

### Automated Focus

- `DocumentStatusServiceTest` / `DocumentMetadataServiceTest`：共享运行状态和事件流摘要投影
- `DocumentControllerTest`：callback 验签、save-status 响应、错误码语义
- `OnlyofficeConfigServiceTest`：`document.url` / `callbackUrl` / `documentServerUrl` 的角色化生成和配置失败语义
- `DocumentStorageServiceTest` / `OnlyofficeImageServiceTest`：SSRF、防大包、内容类型校验
- 全量回归：`cd packages/server && mvn test`

### Manual Focus

- 在多实例或模拟跨实例场景下，编辑页 `save-status` 是否还能看到一致的最近事件
- callback JWT 失败时是否返回明确拒绝，而不是假装 success
- 关键地址配置错误时，系统是否尽早报错而不是生成坏链接
- 远程导入和图片代理被安全策略拒绝时，前端是否能拿到可解释错误

### Recommended Commands

- 快速状态回归：`cd packages/server && mvn -q -DskipITs -Dtest=DocumentStatusServiceTest,DocumentMetadataServiceTest test`
- 运行态协议回归：`cd packages/server && mvn -q -DskipITs -Dtest=DocumentControllerTest,OnlyofficeConfigServiceTest test`
- 远程资源安全回归：`cd packages/server && mvn -q -DskipITs -Dtest=DocumentStorageServiceTest,OnlyofficeImageServiceTest test`
- 全量回归：`cd packages/server && mvn test`
- 编排校验：`docker compose config`

## Planning Guardrails

- 不在本阶段引入完整历史版本中心
- 不在本阶段把来源地址白名单做成唯一信任边界
- 不让前端参与运行态 URL 推导
- 不把远程导入和图片代理安全边界拆成两套不一致模型
- 不让列表页直接依赖事件流表

## Research Summary

最稳的路线是：

- 把 `save-status` 升级为共享数据库驱动的“当前摘要状态 + 最近关键事件”视图
- 用现有 ONLYOFFICE JWT secret 正式接通 callback 验签
- 把 `document.url`、`callbackUrl`、`documentServerUrl` 统一收口为后端角色化生成，并对坏配置尽早失败
- 统一强化远程文档导入和图片代理的 SSRF、防大包、内容类型校验
- 继续以现有 MVC / service 单测和 `mvn test + docker compose config` 作为验证主轴
