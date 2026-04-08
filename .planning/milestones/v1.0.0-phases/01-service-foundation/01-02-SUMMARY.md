---
phase: 01-service-foundation
plan: 02
subsystem: database
tags: [jpa, flyway, postgresql, h2, document-metadata]
requires:
  - phase: 01-01
    provides: 服务化运行时配置与分离部署地址模型
provides:
  - 文档元数据主表与 Flyway 迁移
  - 基于数据库的文档状态与归属模型
  - 以元数据为主线的文档读取、上传、导入、保存回写
affects: [01-03, phase-02, phase-05]
tech-stack:
  added: [spring-data-jpa, flyway-core, flyway-database-postgresql, postgresql, h2]
  patterns: [metadata-first-document-model, database-backed-status]
key-files:
  created:
    - packages/server/src/main/resources/db/migration/V1__create_document_metadata.sql
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/persistence/DocumentMetadataEntity.java
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentMetadataService.java
  modified:
    - packages/server/pom.xml
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStorageService.java
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStatusService.java
    - packages/server/src/main/java/com/earmo/onlyoffice/demo/model/StoredDocument.java
key-decisions:
  - "以 documentId 作为服务内部稳定主键，路径与文件名不再承担主身份"
  - "主文档状态先进入共享元数据模型，避免继续依赖 JVM 内存态"
patterns-established:
  - "元数据优先：先查文档主表，再决定文件读取和回写行为"
  - "状态持久化：editing、saved、failed 等状态通过数据库更新流转"
requirements-completed: [ARCH-02, ARCH-03]
duration: 55min
completed: 2026-03-19
---

# Phase 1 / Plan 02 Summary

**服务端已建立文档元数据主表和共享状态底座，上传、导入、读取、保存回写都改为以数据库元数据驱动。**

## Performance

- **Duration:** 55 min
- **Started:** 2026-03-19T09:15:00Z
- **Completed:** 2026-03-19T10:10:00Z
- **Tasks:** 3
- **Files modified:** 15

## Accomplishments

- 引入 JPA、Flyway、PostgreSQL runtime 与 H2 测试/默认运行支撑，建立共享持久化基础。
- 新建 `document_metadata` 表、实体、仓储和服务，承接文档身份、归属、来源、状态与最近保存信息。
- 重写文档存储与状态服务，让文档文件路径退居实现细节，元数据成为上传、导入、默认文档初始化与 callback 回写的主线。

## Execution Commits

- **实现提交：** `dcc899e` `feat(phase1): 落地服务基础与文档服务API`

## Files Created/Modified

- `packages/server/pom.xml` - 增加 JPA、Flyway、PostgreSQL 与 H2 依赖。
- `packages/server/src/main/resources/db/migration/V1__create_document_metadata.sql` - 创建文档元数据主表与索引。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/persistence/DocumentMetadataEntity.java` - 映射文档主数据与状态字段。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentMetadataService.java` - 封装文档查找、创建、列举和状态更新逻辑。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStorageService.java` - 通过元数据驱动文档初始化、上传、导入与文件读取。
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStatusService.java` - 从内存 Map 迁移为元数据状态适配层。

## Decisions Made

- 默认运行时使用 H2 文件库保证本地可启动，compose 场景使用 PostgreSQL，以兼顾开发便利和分布式部署方向。
- `storageKey` 采用稳定对象键，不再直接依赖原始文件名，以便后续接入 MinIO/COS/OSS。

## Deviations from Plan

None - the plan outcome matches the persistence foundation that was specified.

## Issues Encountered

- 旧实现大量从 `Path` 倒推出文档身份，迁移时需要同步改造 `StoredDocument`、启动初始化逻辑和 callback 状态回写，避免新旧身份模型并存。

## User Setup Required

None - 本阶段未引入外部云存储或额外人工配置步骤。

## Next Phase Readiness

- 文档主数据已经具备共享落点，后续可以继续抽存储策略接口并把文件内容移向对象存储。
- API 层可以直接复用 `DocumentMetadataService` 暴露列表、详情和创建入口。

---
*Phase: 01-service-foundation*
*Completed: 2026-03-19*
