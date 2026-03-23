---
phase: 2
slug: storage-strategy-layer
status: completed
created: 2026-03-23
sources:
  - https://docs.spring.io/spring-boot/reference/features/external-config.html
  - https://docs.spring.io/spring-boot/reference/features/profiles.html
  - https://minio-java.min.io/io/minio/MinioClient.html
  - https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/
---

# Phase 2 Research

## Research Question

Phase 2 需要回答的核心问题不是“怎么把本地文件换成对象存储 SDK”，而是“怎么把当前直接依赖本地 `Path/Files` 的文档读写流程，重构成一个可切换 provider、可保留 local 开发回退、并且不会改坏上层编辑链路的统一存储边界”。

## Current Baseline

- Phase 7 之后，后端已经拆成 `onlyoffice-integration-data` 和 `onlyoffice-integration-service` 两个模块，数据库元数据已不再依赖本地路径推导。
- `DocumentStorageService` 仍然同时承担 storage key 生成、文件系统读写、远程导入下载、bootstrap 文档生成和 callback 覆盖写入，说明它是 Phase 2 最直接的切口。
- `DocumentMetadataService` 已经掌管 `storageKey`、`draft/editing/saved/failed` 等主状态，是文件内容层与元数据层之间最稳定的协调点。
- 当前 `application.yml` 和 `docker-compose.yml` 仍只有 `storage-root` 这一条 local 路径心智，尚未形成正式 provider 配置和 MinIO 联调基线。

## Recommended Technical Direction

### 1. Keep Metadata in Database, Move Binary Content Behind a Strategy Interface

本阶段不应该让对象存储重新成为“文档列表的真相源”。列表、详情、状态仍应由数据库元数据主导；统一存储抽象只负责文档二进制内容的读写、存在性探测和覆盖写入。

这意味着：

- `documentId`、`title`、`status`、`tenantId`、`sourceSystem` 继续来自 `document_metadata`
- provider 只处理 `storageKey -> object bytes / stream / metadata`
- 列表页后续若要展示异常文档，应通过“元数据 + storage exists 检查”生成视图，而不是直接扫 bucket

### 2. Introduce a Provider-Neutral Storage Contract First

在实现 MinIO 之前，应先把 provider-neutral 的合同抽出来。最稳的做法不是把 `DocumentStorageService` 直接分裂成两个完全不同的 service，而是保留它作为业务编排层，再在下面新增：

- `StorageProvider` 枚举
- `DocumentStorageStrategy` 接口
- `StorageWriteRequest` / `StoredObjectResource` 这类最小领域对象
- `StorageProviderResolver` 或 `DocumentStorageStrategyRouter`
- `StorageKeyFactory`

这样可以保证上层 controller、ONLYOFFICE config 生成、元数据服务继续只依赖 `DocumentStorageService`，而不是把 provider 细节向上传染。

### 3. Local Strategy Should Become a Compatibility Adapter, Not the Main Model

讨论阶段已经锁定：

- `MinIO` 是正式默认策略
- `local` 只保留给 `dev/test` 或迁移过渡
- profile 应决定默认 provider，但配置模型要预留后续按 `tenant/sourceSystem` 路由的能力

因此最合理的落地方式是：

- `local` 实现完整遵守统一接口，方便本地开发和快速单测
- `minio` 实现作为正式 provider，进入 compose 和集成测试
- resolver 的输入至少包含 `tenantId`、`sourceSystem`、默认 provider，后续再扩多租户映射时不必重改调用链

### 4. Use Stable Object Keys, Not Filenames

讨论阶段已锁定 `storageKey = tenant/sourceSystem/documentId.ext`。这个决定和现有元数据模型完全兼容，也最利于未来扩 COS / OSS。

关键原因：

- `title` 已经和 `storageKey` 分离，不需要把用户改名操作耦到对象键
- 单 bucket + prefix 分层更适合 MinIO、COS、OSS 之间平移
- 只要 key 中保留 `tenant/sourceSystem/documentId`，后续路由、审计和人工排查都更容易

### 5. Preserve Different Failure Semantics for Create/Import vs Callback

这是 Phase 2 最容易做浅的一块，但实际上必须明确区分两类写入：

- `create/upload/import`
  这是首次建档，目标语义是“尽量原子成功”。如果对象写失败，不能留下元数据；如果元数据已写而对象失败，必须补偿删除或回滚。
- `callback overwrite`
  这是已有文档的新版本回写。失败时不能把旧文件一并破坏，而是要保留最后一次成功版本，并把元数据状态标记为 `failed`。

当前 ONLYOFFICE callback 流程已经先写状态、再下载覆盖文件；后续只要把“覆盖写入”改到 strategy 里，并保证异常时不先删旧对象，就能延续这一产品语义。

## Domain Findings

### Spring Boot Configuration Model

Spring Boot 官方文档对 external config 和 profile 都提供了稳定支持，因此本阶段最适合把当前单层的 `storageRoot` 扩展成层次化配置，例如：

- `onlyoffice.integration.storage.default-provider`
- `onlyoffice.integration.storage.routing.source-systems.native`
- `onlyoffice.integration.storage.routing.tenants.tenant-a`
- `onlyoffice.integration.storage.local.root`
- `onlyoffice.integration.storage.minio.endpoint`
- `onlyoffice.integration.storage.minio.bucket`
- `onlyoffice.integration.storage.minio.access-key`
- `onlyoffice.integration.storage.minio.secret-key`
- `onlyoffice.integration.storage.minio.path-style-access`

这样既能支持 `dev/test=local`、`prod=minio` 的 profile 默认值，也能为后续多租户或按来源路由保留结构空间。

### MinIO Java SDK Capability

MinIO Java SDK 官方 API 已覆盖本阶段需要的最小能力：

- `putObject`：写入或覆盖对象
- `getObject`：读取对象内容
- `statObject`：检查对象是否存在并读取元信息
- `removeObject`：建档失败时执行补偿删除

这足以支撑：

- 上传/导入后的最终写入
- callback 覆盖写回
- 列表/详情层的对象存在性探测
- 元数据创建失败时的 best-effort 回滚

本阶段不需要引入 MinIO 专属 bucket 策略、生命周期规则或版本控制能力；保持 provider-neutral 的 key 语义即可。

### ONLYOFFICE Callback Constraint

ONLYOFFICE 官方 callback 文档仍然要求服务端在 `status=2/6` 等可保存状态时，通过 callback body 提供的 `url` 下载最新文件。对当前项目而言，这意味着：

- callback 下载动作继续放在 service 层是合理的，因为它是“业务流程的一部分”
- strategy 只需要负责 `overwrite(storageKey, bytes, contentType)` 这一落盘动作
- 只要 overwrite 在开始写新内容前不主动删除旧对象，就能满足“失败保留旧版本”的决策

### Test Strategy

本阶段适合继续以 Maven + JUnit 5 为主，并把 MinIO 验证分成两层：

- 单元层：`LocalDocumentStorageStrategyTest`、`StorageProviderResolverTest`
- 集成层：`MinioDocumentStorageStrategyTest` 使用 Testcontainers 的 `GenericContainer` 启动 MinIO

这样比直接依赖本地手工起 MinIO 更稳，也能让 `mvn test` 真正覆盖 STOR-02。

## Rejected or Deferred Options

### Keep All Storage Logic Inside `DocumentStorageService`

不建议。这样会继续把 provider 切换、配置、键策略和业务编排揉成一团，Phase 3 到 Phase 5 只会越来越难拆。

### Make MinIO the Only Strategy and Delete Local Immediately

不建议。讨论阶段已经锁定 local 要保留为开发/过渡兼容；直接删除只会提高本地调试门槛，也会让回归测试更脆弱。

### List Documents by Scanning the Bucket

不建议。文档列表是产品主模型，必须继续以数据库元数据为主；对象存储只负责验证内容是否仍可读。

### Pull Remote URLs Directly Inside MinIO Strategy

不建议。远程导入的网络访问属于安全边界的一部分，继续放在 service 层更清晰，也更符合后续安全治理方向。

## Implementation Implications for Planning

Phase 2 最稳的拆法是 3 个 plan、3 个 wave：

1. 先抽统一存储接口、key 工厂、provider resolver，并把 local 变成策略实现
2. 再引入 MinIO provider、compose 联调和 callback / create / import 的一致语义
3. 最后固化配置、路由扩展约定、列表可用性投影和接入文档

这样可以确保每一波都有明确增量：

- Wave 1：即便还没接 MinIO，代码也已经从本地文件系统细节里松绑
- Wave 2：正式跑通 MinIO 并验证关键链路
- Wave 3：把扩展面、文档、配置、验证基线收口，避免后续 Phase 3/4 重做

## Validation Architecture

本阶段验证建议继续用“快速单测 + MinIO 集成测试 + compose 结构检查”的组合。

### Automated Focus

- `OnlyofficeIntegrationProperties` 新增 storage/provider/profile 配置绑定
- `LocalDocumentStorageStrategy` 的读写、exists、非法 key 防逃逸
- `MinioDocumentStorageStrategy` 的 put/get/stat/remove 行为
- `DocumentStorageService` 在 create/upload/import 与 callback 场景下的补偿和保留旧版本语义
- `DocumentApiController` 或对应投影层在对象缺失时仍返回文档、同时暴露异常可见性

### Manual Focus

- `docker-compose.yml` 是否新增 `minio` 服务、bucket 初始化或启动参数
- 默认 profile 是否仍允许 local 开发回退，而正式 compose / prod 语义转向 MinIO
- `docs/minimal-integration.md` 是否明确说明 `tenant/sourceSystem/documentId.ext` 的对象键结构和 provider-neutral 扩展规则

### Recommended Commands

- 快速验证：`cd packages/server && mvn -q -DskipITs test`
- 完整验证：`cd packages/server && mvn test`
- 构建验证：`cd packages/server && mvn -q -DskipITs package`
- compose 结构检查：`docker compose config`

## Planning Guardrails

- 不在本阶段实现 COS / OSS 正式 provider，但接口与配置模型必须明确为可扩展
- 不把插图资源强行并入文档主存储路径，只预留后续扩展面
- 不让前端接管对象 key 或真实文件 URL 生成，仍由后端围绕 `documentId` 和 `storageKey` 统一输出
- 不回退 Phase 7 的模块边界，provider、resolver、storage config 都优先落在 `onlyoffice-integration-service` 模块

## Research Summary

最稳的路线是：

- 保留数据库元数据作为文档列表和状态的真相源
- 新增 provider-neutral 存储合同，让 `DocumentStorageService` 从本地 `Path/Files` 细节中解耦
- 用 `tenant/sourceSystem/documentId.ext` 作为统一对象键结构
- 将 MinIO 作为正式默认 provider 跑通上传、导入、读取和 callback 覆盖写回
- 用 profile + resolver + 文档约定把 local 兼容、MinIO 正式路径、未来 COS/OSS 扩展一次性铺平
