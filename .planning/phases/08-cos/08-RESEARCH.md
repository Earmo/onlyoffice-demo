---
phase: 8
slug: cos
status: completed
created: 2026-03-26
sources:
  - .planning/ROADMAP.md
  - .planning/REQUIREMENTS.md
  - .planning/STATE.md
  - packages/server/pom.xml
  - packages/server/onlyoffice-integration-service/pom.xml
  - packages/server/onlyoffice-integration-service/src/main/resources/application.yml
  - packages/server/onlyoffice-integration-service/src/main/resources/application-windows-debug.yml
  - packages/server/onlyoffice-integration-service/src/test/resources/application.yml
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeConfigService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageProvider.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/DocumentStorageStrategy.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageProviderResolver.java
  - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/minio/MinioDocumentStorageStrategy.java
  - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/minio/MinioDocumentStorageStrategyTest.java
---

# Phase 8 Research

## Research Question

Phase 8 需要回答的核心问题不是“再补一个云存储实现”或“把类名拆成接口和实现”这么局部，而是如何在不打断现有交付基线的前提下，同时完成 4 件会互相影响的事：

1. 把后端配置收敛成 `dev / test / prod` 的正式环境模型；
2. 把 `service` 包从“全是具体类”升级成清晰的接口边界；
3. 在现有 provider-neutral 存储抽象上新增腾讯云 COS；
4. 显著提高关键代码和配置的中文注释密度，但不把 Phase 8 做成全仓库注释翻新工程。

当前阶段没有单独的 `08-CONTEXT.md`，所以本次 research 以 `ROADMAP.md`、`REQUIREMENTS.md`、最近用户明确提出的 5 条诉求，以及当前代码结构为锁定输入。

## Current Baseline

- `packages/server/onlyoffice-integration-service/src/main/resources/` 目前只有 `application.yml` 和 `application-windows-debug.yml` 两套配置，`dev / test / prod` 还没有正式拆分。
- `packages/server/onlyoffice-integration-service/src/test/resources/application.yml` 已经承载了一部分测试环境配置，但它是测试资源文件，不是正式环境 profile 的一部分。
- 根 `packages/server/pom.xml` 当前 `artifactId` 为 `onlyoffice-integration-service`，而真正的服务模块 `packages/server/onlyoffice-integration-service/pom.xml` 的 `artifactId` 是 `onlyoffice-integration-starter`，与模块路径语义相反。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/` 目录下当前都是具体实现类，例如 `OnlyofficeConfigService`、`DocumentStorageService`、`DocumentStatusService`、`AccessAuditService`，尚未形成 `接口 + impl` 结构。
- 现有存储层已经有比较稳定的 provider-neutral 基线：
  - `StorageProvider` 只有 `LOCAL`、`MINIO`
  - `DocumentStorageStrategy` 已抽象为 `exists/read/writeNew/overwrite/delete`
  - `StorageProviderResolver` 已支持 `tenant > sourceSystem > defaultProvider` 路由优先级
  - `MinioDocumentStorageStrategy` 已经给新增云厂商实现提供了清晰参考
- 代码里已经开始有中文注释，但注释分布不均：像 `DocumentStorageService`、`OnlyofficeConfigService`、`OnlyofficeIntegrationProperties` 相对较多，其他 service 和新加的配置块还不够系统化。

## Domain Findings

### 1. Profile Split Should Be Additive, Not Destructive

当前 `application.yml` 已经承载大量默认值、环境变量占位和 Windows 本地调试兼容逻辑。如果 Phase 8 直接“把所有内容搬到某个 profile”，很容易打断现有：

- `windows-debug` 本地调试 profile
- 测试资源中的 H2/Flyway 配置
- README 和交付文档里的默认示例

更稳的做法是：

- `application.yml` 只保留公共配置与 `spring.profiles.active` 的统一入口；
- 新增 `application-dev.yml`、`application-test.yml`、`application-prod.yml` 承载环境差异；
- `application-windows-debug.yml` 继续保留为本地断点调试 profile，但语义上应明确它是叠加在正式环境模型之外的本地 override，而不是替代 `dev`。

### 2. The Current Maven Coordinates Are Semantically Reversed

用户要求“`artifactId` 和 `parent.artifactId` 交换”，结合当前结构来看，最自然的目标状态是：

- 聚合父工程 `packages/server/pom.xml` 的 `artifactId` 使用 `onlyoffice-integration-starter`
- 真正可运行的服务模块 `packages/server/onlyoffice-integration-service/pom.xml` 的 `artifactId` 使用 `onlyoffice-integration-service`

这样可以同时满足：

- 父工程保持“starter 聚合工程”的语义；
- 服务模块名与目录名、Spring Boot 运行入口和模块路径一致；
- 后续如果再新增 `onlyoffice-integration-web` 或其他 starter 子模块，不会继续放大命名歧义。

### 3. Service Interface Split Is Best Done as a Facade Contract, Not as Pure Ceremony

当前 service 包的类大多都是“对外业务门面”，例如：

- `OnlyofficeConfigService`
- `DocumentStorageService`
- `DocumentMetadataService`
- `DocumentStatusService`
- `AccessAuditService`
- `OnlyofficeImageService`
- `OnlyofficeJwtService`
- `RemoteResourceSecurityService`

这些类都适合作为接口边界，但不适合机械拆分成“接口无意义、实现完全 1:1 copy”然后结束。更有价值的目标状态是：

- controller、其他 service、配置类统一依赖接口；
- `impl/` 目录承载当前默认实现；
- 关键实现方法补完整中文注释，解释“为什么要这样做”和“步骤顺序”；
- `DocumentNotFoundException` 这类异常类保持原位，不强行接口化。

### 4. COS Support Can Reuse the Existing Strategy Shape

现有 `MinioDocumentStorageStrategy` 已经说明新增云存储最合理的路径不是改上层流程，而是复用同一套抽象：

- 在 `StorageProvider` 新增 `COS`
- 在 `OnlyofficeIntegrationProperties.StorageProperties` 新增 `cos` 配置块
- 新增 `cos/` 目录下的 client factory 和 strategy
- `StorageProviderResolver`、`StorageProviderResolverTest`、配置文档一起扩展

关键点在于：COS 的实现要继续遵守 `storageKey` 才是业务身份真相源，不能把 bucket、region 或对象 URL 泄漏到上层 controller/service 语义里。

### 5. Detailed Comments Need a Priority Map

用户要求“更详细更完整的代码注释和步骤解释”，但如果不设边界，很容易变成全仓库大面积机械加注释。Phase 8 更合适的策略是只覆盖以下高价值区域：

- 环境配置与 profile 切换入口
- service 接口的职责约定
- `impl` 中的关键业务编排方法
- COS 存储策略和 provider 路由相关代码

也就是说，这一阶段的注释目标是“让后续维护者看懂关键流程”，而不是“每个 getter/setter 都写注释”。

## Recommended Technical Direction

### 1. Use Three Plans in Two Waves

最稳的执行拆法是：

- Wave 1
  - `08-01`：环境拆分 + Maven 坐标命名收敛
  - `08-02`：service 接口化 + 注释增强
- Wave 2
  - `08-03`：COS provider 落地并接回现有存储路由

这样可以让配置和 service 结构并行推进，再让 COS 支持建立在已经稳定的坐标/配置/接口边界之上。

### 2. Treat `application.yml` as the Selector and Shared Base

`application.yml` 应承担两类职责：

- 统一声明 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`
- 保留所有环境共享的默认行为，例如：
  - 端口
  - `forward-headers-strategy`
  - multipart 限制
  - OpenAPI / Knife4j 基础开关
  - `onlyoffice.integration.access-context` 的共享默认值
  - 存储路由映射骨架

而数据库、对象存储、公开地址、私网安全开关这些环境差异明显的项，应拆进 `application-dev.yml`、`application-test.yml`、`application-prod.yml`。

### 3. Make Interface Names Stable and Move Concrete Logic into `impl`

推荐目标状态：

- `service/DocumentStorageService.java` 变成接口
- `service/impl/DocumentStorageServiceImpl.java` 承载现有逻辑
- 其他 facade 类同理

并保持下面几条约束：

- controller 只依赖接口
- impl 类名保持 `XxxServiceImpl`
- 异常类和纯值对象不强行迁移
- 测试优先覆盖对外行为，不因接口化把测试重心挪到“只是类名变化”

### 4. Use COS SDK Behind a Thin Factory Boundary

Phase 8 的 COS 支持不建议把腾讯云 SDK 直接散落到多个类里。更合理的方式是：

- 新增一个 `CosClientFactory`
- `CosDocumentStorageStrategy` 只依赖工厂和统一配置
- 继续实现 `DocumentStorageStrategy`

这样可以把：

- 凭证构造
- region、bucket、endpoint 初始化

都收口到一个地方，同时让单测更容易做替身或局部 mock。

### 5. Keep Documentation and Config Matrix in Sync with Code

因为 Phase 6 已经建立了交付文档结构，Phase 8 新增 `dev / test / prod` 和 COS 后，至少需要同步以下交付面：

- `README.md`
- `docs/configuration-matrix.md`
- `docs/standalone-deployment.md`
- 如有必要，`docs/microservice-integration.md`

否则代码虽支持 profile/COS，交付面仍会停留在 MinIO + 单配置文件时代。

## Validation Architecture

Phase 8 的验证应该继续以“快速回归 + 全量回归 + 配置文档检查”组合完成，而不是额外引入新的测试平台。

### Automated Focus

- 配置 profile 绑定与属性回归：
  - `OnlyofficeIntegrationPropertiesTest`
  - 至少一次 `spring-boot:run` 或 `mvn test` 级配置加载验证
- service 接口化回归：
  - `OnlyofficeConfigServiceTest`
  - `DocumentStorageServiceTest`
  - `DocumentMetadataServiceTest`
  - `DocumentStatusServiceTest`
  - `AccessAuditServiceTest`
- 存储 provider 扩展回归：
  - `StorageProviderResolverTest`
  - `MinioDocumentStorageStrategyTest`
  - 新增 `CosDocumentStorageStrategyTest`
- 构建与模块命名回归：
  - `cd packages/server && mvn -q -DskipITs package`

### Manual Focus

- `dev / test / prod` profile 选择是否能被 README 和配置矩阵清楚解释
- `windows-debug` 是否仍能作为 Windows 本地断点调试 profile 使用
- COS 配置说明是否足以让后续开发者知道需要的必填项和默认值

### Recommended Commands

- 快速属性与 service 回归：
  - `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=OnlyofficeIntegrationPropertiesTest,OnlyofficeConfigServiceTest,DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentStatusServiceTest,AccessAuditServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 快速 storage/provider 回归：
  - `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs -Dtest=StorageProviderResolverTest,MinioDocumentStorageStrategyTest,CosDocumentStorageStrategyTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 后端完整回归：
  - `cd packages/server && mvn test`
- 构建与坐标校验：
  - `cd packages/server && mvn -q -DskipITs package`

## Planning Guardrails

- 不把 Phase 8 做成“所有代码都接口化”的纯形式主义重构；只处理 `service` 包的业务门面
- 不在本阶段把 Windows 调试 profile 删除或并入正式 profile，避免打断已交付的本地调试方案
- 不改变 `tenant > sourceSystem > defaultProvider` 的既有路由优先级
- 不让 COS 实现绕开 `DocumentStorageStrategy` 抽象直接进入业务层
- 不把“详细注释”做成逐行注释堆砌，而是优先解释步骤、边界和补偿语义

## Research Summary

最稳的路线是：

- 用 `application.yml + application-dev/test/prod.yml + application-windows-debug.yml` 形成“正式环境模型 + 本地调试覆盖”的配置层次；
- 交换父工程与服务模块的 Maven 坐标语义，让聚合工程回到 `starter`，运行模块回到 `service`；
- 把 `service` 包收敛成 `接口 + impl` 结构，并在关键编排方法补充完整中文注释；
- 基于现有 `DocumentStorageStrategy` 与 `StorageProviderResolver` 直接落地腾讯云 COS；
- 同步测试和文档，让 Phase 8 的新增能力既能运行，也能被后续维护者看懂和复用。
