---
phase: 1
slug: service-foundation
status: completed
created: 2026-03-19
sources:
  - https://docs.spring.io/spring-boot/reference/features/external-config.html
  - https://docs.spring.io/spring-boot/reference/data/sql.html
  - https://docs.spring.io/spring-boot/how-to/data-initialization.html
  - https://api.onlyoffice.com/docs/docs-api/usage-api/config/editor/
  - https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/
---

# Phase 1 Research

## Research Question

Phase 1 需要回答的不是“怎么把 demo 继续跑起来”，而是“怎么把它收束成一个可独立部署、可被其他系统集成、且不再依赖单机状态的文档服务基础层”。

## Current Baseline

- 后端已经是可运行的 Spring Boot 3.3.12 服务，配置集中在 `DemoProperties`，这是可演进的起点。
- 前端已经是独立的 Vue/Vite 包，并通过 nginx 聚合 `/api` 与 ONLYOFFICE 路径，说明“官方前端作为客户端”这条路已被验证。
- 文档元数据、保存状态和文件定位仍然依赖 `DocumentStorageService` 与 `DocumentStatusService` 的本地文件系统 / 内存模型，这与 `ARCH-03` 直接冲突。
- 当前地址模型只有 `documentServerUrl` 和 `internalBaseUrl` 两段式意识，还缺少真正可分布式部署的 public/internal/client 边界。

## Recommended Technical Direction

### 1. Keep the Existing Runtime Stack

本阶段不需要更换主栈，继续使用现有 Spring Boot + Vue + ONLYOFFICE + nginx 组合最稳。Phase 1 的价值在于重新定义边界，而不是替换成熟的基础集成。

### 2. Introduce Shared Metadata Persistence Now

建议在 Phase 1 就引入关系型元数据存储，推荐默认以 PostgreSQL 为生产级共享元数据存储，测试场景可继续使用 Spring Boot 测试基线。

原因：
- `ARCH-03` 要求核心共享状态不能依赖单机内存或本地目录。
- 文档主模型天然适合关系型表：`document_id`、`tenant_id`、`owner_user_id`、`source_system`、`external_document_id`、`title`、`storage_key`、`status`、审计时间戳。
- Phase 2 需要引入存储策略层，若 Phase 1 不先把元数据从本地文件路径中剥离，后续 MinIO / COS / OSS 会被路径推导强耦合拖住。

### 3. Use Flyway for Schema Control

Phase 1 适合同时引入 Flyway，建立 `db/migration` 管理方式，而不是依赖 JPA 自动建表。

原因：
- Spring Boot 官方文档对 SQL 数据库、JPA 和数据库初始化都提供现成集成路径。
- 对一个要支持独立部署和微服务接入的服务来说，数据库结构需要可审计、可回放、可版本化。
- 后面分阶段补文档表、状态字段、外部绑定和索引时，Flyway 比 `ddl-auto` 更稳。

### 4. Keep Service Contracts Headless-First

官方前端继续存在，但应该降格为一等客户端，而不是服务边界的定义者。

这意味着：
- 前端只消费列表、详情、打开编辑器、上传、导入等服务 API。
- ONLYOFFICE `document.url`、`editorConfig.callbackUrl`、JWT、下载地址都由后端生成。
- 外部系统先调服务端 API 建立文档上下文，再决定是跳转官方前端还是用自己的前端消费能力。

### 5. Split Address Semantics Explicitly

建议把当前 demo 配置拆成至少三类地址语义：
- `publicBaseUrl`：浏览器、外部系统、前端跳转时可访问的服务公开入口
- `internalBaseUrl`：ONLYOFFICE 容器或内网服务回调、拉取文件时访问的 API 入口
- `documentServerUrl`：浏览器可加载 ONLYOFFICE 前端资源的公开地址

当前 `internalBaseUrl + nginx 同源代理` 模式仍然保留，但要从“唯一模式”降为“聚合部署模式之一”。

## Domain Findings

### Configuration Model

Spring Boot 官方文档明确支持通过 `@ConfigurationProperties` 做外部化配置绑定与校验。对本项目而言，最适合的演进方式是把 `DemoProperties` 拆成更稳定的服务化结构，例如：

- `demo.runtime.public-base-url`
- `demo.runtime.internal-base-url`
- `demo.onlyoffice.document-server-url`
- `demo.auth.trusted-token`
- `demo.storage.mode`
- `demo.persistence.enabled`

是否保留 `demo.*` 前缀属于实现细节，但配置分层必须从 demo 语义转为运行时语义。

### Metadata Model

Phase 1 的主文档模型建议最少覆盖：

- `documentId`
- `tenantId`
- `ownerUserId`
- `sourceSystem`
- `externalDocumentId`
- `title`
- `storageKey`
- `fileType`
- `documentType`
- `status`
- `lastCallbackStatus`
- `lastErrorMessage`
- `createdAt`
- `updatedAt`
- `lastOpenedAt`
- `lastSavedAt`

这里把 `status` 直接放在主表中，是为了匹配已锁定的 `draft / editing / saved / failed / archived` 决策，同时替换当前 `DocumentStatusService` 的内存态。

### API Boundary

Phase 1 不需要把最终 API 一次性做满，但应该先明确 3 类入口：

- 文档主数据入口：列表、详情、创建、上传、导入
- 编辑入口：根据 `documentId` 生成 editor session/config
- ONLYOFFICE 回调入口：回调接收、状态更新、保存回写

当前 `DocumentController` 把这些动作混在一起还能运行，但 Phase 1 更适合开始按“文档服务 API”而不是“demo controller”组织接口和 DTO。

### ONLYOFFICE Constraints

ONLYOFFICE 官方文档强调：
- `editorConfig.callbackUrl` 必须是文档存储服务可访问的绝对 URL。
- 回调地址会根据用户、状态和会话上下文被实际使用。
- `status` 2、3、6、7 等回调会携带可保存文档的下载地址或错误语义。

因此：
- 回调入口必须使用服务侧可控的绝对地址生成，不能靠前端猜 URL。
- Phase 1 虽然不必一次解决所有分布式 callback 细节，但必须先为“公开地址”和“内网地址”分离打下配置基础。

## Rejected or Deferred Options

### Continue Using Local Filesystem as the Source of Truth

不建议。可以暂时继续把“文件内容”保存在本地目录，直到 Phase 2 引入 MinIO，但文档元数据和状态不能继续从本地路径反推。

### Use External IDs as the Primary Key

不建议。已锁定为服务内生成 `documentId`，这更适合多系统接入和长期演进。

### Let the Frontend Assemble Editor URLs

不建议。这样会把部署复杂度、ONLYOFFICE 地址规则和安全边界散到前端，和 `headless-first` 方向相反。

## Implementation Implications for Planning

Phase 1 的计划应该拆成三块：

1. 地址与部署配置重构
2. 文档元数据与共享持久化基础
3. 微服务接入 API 与请求上下文边界

其中第 1 和第 2 块可以并行，第 3 块依赖前两块收束后的配置和主模型。

## Validation Architecture

本阶段验证建议以“后端快速反馈 + 配置文件断言 + 少量手工集成检查”为主。

### Automated Focus

- `DemoProperties` 的新配置绑定与默认值
- 文档元数据实体 / 仓储 / 服务
- `DocumentStatusService` 到持久化模型的迁移结果
- `DocumentController` 或拆分后的 API 控制器在 `list / create / upload / editor-config` 路径上的契约行为

### Manual Focus

- `docker-compose.yml` 是否显式增加数据库服务与环境变量
- `packages/web/nginx.conf` 是否仍支持聚合部署入口
- ONLYOFFICE 在 compose demo 中是否仍能通过后端生成的绝对地址完成加载

### Recommended Commands

- 后端快速验证：`mvn -q -DskipITs test`
- 后端完整验证：`mvn test`
- 前端构建验证：`pnpm build`
- compose 结构检查：`docker compose config`

## Planning Guardrails

- Phase 1 不实现 MinIO / COS / OSS 正式策略，只为后续存储层留出 `storageKey + metadata` 基础。
- Phase 1 不实现强耦合登录体系，只定义服务到服务认证与用户上下文透传边界。
- Phase 1 不重做首页 UI，但如果前端代码需要调整，只能做契约消费层的最小适配。

## Research Summary

最稳的路线是：
- 保留现有运行栈
- 引入共享元数据数据库与 Flyway
- 将配置显式拆成 public/internal/document-server 几类地址
- 把文档主模型与保存状态从本地文件路径 / 内存态中剥离
- 让服务 API 先于官方前端成为正式产品边界

这条路径能直接覆盖 `ARCH-01`、`ARCH-02`、`ARCH-03`，同时也为 Phase 2 到 Phase 5 的存储、用户、列表和分布式 callback 链路打基础。
