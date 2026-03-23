---
phase: 7
slug: module-boundaries-and-repository-refactor
status: completed
created: 2026-03-23
sources:
  - https://spring.io/guides/gs/multi-module/
  - https://maven.apache.org/guides/mini/guide-multiple-modules.html
  - https://maven.apache.org/pom.html
  - https://mybatis-flex.com/zh/base/configuration.html
  - https://mybatis-flex.com/zh/base/querywrapper.html
  - https://mybatis-flex.com/zh/intro/use-mybatis-native.html
---

# Phase 7 Research

## Research Question

Phase 7 需要解决的问题不是“把几个类随手改名”，而是把当前后端从 demo 风格单模块工程，收束成一个可继续演进的 starter/service/data 分层结构，同时清掉 Mapper 上的注解 SQL 和字段命名债务。

## Current Baseline

- 当前后端仍是单个 Maven `jar` 工程：`packages/server/pom.xml` 同时承载 Spring Boot 可执行应用、数据访问、Flyway 迁移和测试。
- `DocumentMetadataMapper` 还在使用 `@Select` 编写自定义 SQL，和你这次明确提出的 repository 约束冲突。
- `DocumentMetadataEntity` 与 `V1__create_document_metadata.sql` 仍保留 `created_at`、`updated_at`、`last_opened_at` 这类历史命名。
- 项目里仍有大量 `OnlyofficeDemoApplication`、`onlyoffice-demo-server`、`demo:`、`DEMO_*` 等遗留命名。
- 当前工作区已经开始把 `persistence` 包迁到 `entity`，说明数据层重构已经开始，但还没有形成稳定模块边界。

## Recommended Technical Direction

### 1. Split Backend into Parent + Data Module + Service Module

Spring 官方 multi-module 指南建议根项目使用聚合 POM，库模块不要打可执行 boot jar，应用模块再依赖库模块。Maven 官方文档也明确说明聚合 POM 通过 `<modules>` 收集并按依赖关系排序构建。

对当前仓库，最稳的目标结构是：

- `packages/server/pom.xml`：父聚合 POM，`packaging` 为 `pom`
- `packages/server/onlyoffice-integration-data`：数据库与持久化模块，不打 boot 可执行 jar
- `packages/server/onlyoffice-integration-service`：Spring Boot 服务模块，依赖 data 模块并生成运行产物

这样能直接覆盖 `ARCH-04` 和 `MOD-01`，并避免把数据库代码继续埋在服务入口模块里。

### 2. Keep Custom Queries Out of Mapper Annotations

MyBatis-Flex 官方文档说明：

- `BaseMapper` 已经覆盖常见 CRUD
- 自定义查询既可以走 XML，也可以通过 `QueryWrapper` 组装
- `mapper-locations` 是框架支持的标准扩展点

结合你的约束，Phase 7 不应继续在 `DocumentMetadataMapper` 上保留 `@Select`。更合适的做法是：

- `Mapper` 只保留 `BaseMapper<DocumentMetadataEntity>` 基础能力
- 新建 `DocumentMetadataRepository`
- repository 在类中显式提供 `listByTenant(...)`、`findBySourceSystemAndExternalDocument(...)` 之类领域查询方法
- repository 内部优先用 `QueryWrapper` + `BaseMapper` 完成查询；仅在 QueryWrapper 表达力不足时才引入 XML

这能把“领域查询入口”固定在 repository 层，而不是把 SQL 注解分散到 Mapper 接口上。

### 3. Rename Time and User Columns with an Explicit Flyway Migration

当前 `document_metadata` 表已经由 Flyway `V1__create_document_metadata.sql` 创建，不能直接去改历史迁移。Phase 7 应新增 `V2` 迁移，显式重命名列并同步 Java 字段。

建议的最小重命名集合：

- `owner_user_id` -> `owner_user`
- `created_at` -> `created_time`
- `updated_at` -> `updated_time`
- `last_opened_at` -> `last_opened_time`
- `last_callback_at` -> `last_callback_time`
- `last_saved_at` -> `last_saved_time`

生成的 `target/generated-sources/annotations/...TableDef` 属于 APT 产物，不能手改，正确做法是改实体注解和迁移脚本后重新编译生成。

### 4. Rename Demo-Facing Names at the Same Boundary Cut

这轮不应该只改少数类名，否则会出现模块新、命名旧的混合状态。建议在模块切分同时完成下面几类收敛：

- 应用入口：`OnlyofficeDemoApplication` -> `OnlyofficeIntegrationStarterApplication`
- Artifact / Name：`onlyoffice-demo-server` -> `onlyoffice-integration-starter`
- 配置根与环境变量：从 `demo` / `DEMO_*` 收敛到新的 starter 语义命名
- 包结构：从 `com.earmo.onlyoffice.demo` 收敛到 `com.earmo.onlyoffice.integration`

这一步要和模块切分一起做，避免出现“模块已经叫 starter，但代码包名仍是 demo”的双重心智负担。

## Domain Findings

### Build Boundary

Spring 的 multi-module 指南强调 library module 不应保留 Spring Boot Maven Plugin 的可执行打包职责。对应到本项目：

- data 模块应该是普通 jar
- service 模块才保留 `spring-boot-maven-plugin`
- Dockerfile 和 compose 指向 service 模块产出的 boot jar

### Package and Scan Boundary

当前启动类上有：

- `@ConfigurationPropertiesScan`
- `@MapperScan("com.earmo.onlyoffice.demo.entity")`

切模块后，服务模块要么显式扫描 data 模块包，要么统一把根包改到 `com.earmo.onlyoffice.integration`，让扫描自然覆盖 service/data 子包。

### Database Access Boundary

从当前代码看，`DocumentMetadataService` 已经明显在表达领域动作，而 `Mapper` 里夹了两个领域查询。更合理的层次应该是：

- `entity`：表映射
- `mapper`：基础 CRUD
- `repository`：领域查询与 QueryWrapper 组装
- `service`：业务状态流转

这会比“service 直接依赖带 SQL 注解的 Mapper”更稳定，也更适合后面继续扩展其它 repository。

## Rejected or Deferred Options

### Keep a Single `packages/server` Jar and Only Rename Packages

不建议。这样只能得到“名称看起来更干净”，但服务和数据边界依然耦在一起，`ARCH-04` 无法真正落地。

### Replace `@Select` with XML but Keep SQL Methods on Mapper Interface

不建议。虽然技术上可行，但不满足“自定义查询应该创建 repository 类”的约束。SQL 从注解挪到 XML 还不够，查询入口本身也要迁到 repository。

### Manually Edit Generated `TableDef` Sources

不建议。`target/generated-sources/annotations` 是编译产物，应该通过实体注解和 APT 重新生成，而不是把生成代码当源码维护。

## Implementation Implications for Planning

Phase 7 适合拆成三块：

1. 建立后端 Maven 多模块骨架与依赖方向
2. 重构数据访问层与表字段命名规范
3. 清理 demo/starter 命名并把服务模块重新接通

这三块中：

- 第 1 块必须先做，因为后两块都依赖稳定模块落点
- 第 2 块处理 repository 与 schema 迁移
- 第 3 块统一处理应用入口、artifact、配置前缀、Docker 路径和文档命名

## Validation Architecture

本阶段验证应以“后端 reactor 构建 + 数据访问测试 + 配置/命名 grep 断言”为主。

### Automated Focus

- `packages/server/pom.xml` 是否变成父聚合 POM，并声明 service/data 子模块
- `DocumentMetadataMapper` 是否去掉 `@Select`
- `DocumentMetadataRepository` 是否提供领域查询方法
- Flyway `V2` 是否完成列重命名
- 服务模块是否仍能启动并跑通当前测试

### Manual Focus

- Dockerfile / compose 是否指向新的服务模块 jar
- 配置根和环境变量是否从 `demo` 切到 starter 语义
- 包名和目录名是否没有继续扩散 `demo` 命名

### Recommended Commands

- 快速验证：`cd packages/server && mvn -q -DskipITs test`
- 完整验证：`cd packages/server && mvn test`
- 结构检查：`cd packages/server && mvn -q help:effective-pom`
- 命名扫描：`rg -n "onlyoffice-demo|OnlyofficeDemo|@Select\\(" packages/server --glob "!**/target/**"`

## Planning Guardrails

- 不直接编辑 `target/generated-sources/annotations` 下的 APT 生成文件。
- 不在本阶段继续扩存储策略或用户上下文能力，避免和 Phase 2 / 3 目标交叉。
- 模块拆分后，服务模块必须单向依赖数据库模块，不能再反向引用 service 包。
- 若配置根要改名，必须同步更新 `application.yml`、测试配置、Dockerfile、README 和 compose 环境变量。

## Research Summary

Phase 7 最稳的路线是：

- 先把 `packages/server` 收成 Maven 聚合父工程
- 再拆出 `onlyoffice-integration-data` 与 `onlyoffice-integration-service`
- 将自定义查询提升到 repository 层，Mapper 只留基础 CRUD
- 用新的 Flyway 迁移把 `*_at` / `*_user_id` 收敛到 `*_time` / `*_user`
- 最后统一清理 `onlyoffice-demo` 与 `demo` 命名

这样可以一次把模块边界、数据访问约束和命名债务收干净，而不是在现有单模块结构里继续打补丁。
