---
phase: 02-storage-strategy-layer
plan: 02
subsystem: minio-provider
tags: [minio, testcontainers, callback, upload, import]
requires:
  - phase: 02-01
    provides: 统一存储合同与 provider 路由
provides:
  - MinIO 正式 provider 与客户端工厂
  - create/upload/import 的补偿式建档语义
  - callback 回写保留旧版本的写入路径
affects: [02-03, phase-05, phase-06]
tech-stack:
  added: [minio-sdk, okhttp-jvm, testcontainers]
  patterns: [object-first-then-metadata, callback-overwrite, provider-based-compose]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/minio/MinioClientFactory.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/minio/MinioDocumentStorageStrategy.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/minio/MinioDocumentStorageStrategyTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java
  modified:
    - packages/server/onlyoffice-integration-service/pom.xml
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java
    - docker-compose.yml
key-decisions:
  - "compose 联调默认 provider 切到 minio，local 只通过配置或 profile 保留"
  - "建档类动作先写对象再落元数据，元数据失败时做 best-effort 删除补偿"
  - "callback 覆盖写入失败时保留旧对象版本，由状态层标记 failed"
patterns-established:
  - "MinIO as baseline：正式联调围绕对象存储验证，避免继续把本地目录当正式路径"
  - "自动化 provider 测试：用 Testcontainers 启动 MinIO 验证读写、覆盖、删除与存在性检查"
requirements-completed: [STOR-01, STOR-02]
duration: 55min
completed: 2026-03-23
---

# Phase 2 / Plan 02 Summary

**MinIO 已经成为正式可用 provider，上传、导入和 callback 回写都能通过统一存储接口跑通。**

## Accomplishments

- 为 service 模块引入 `io.minio:minio` 与 Testcontainers 依赖，并修正 MinIO SDK 所需的 `okhttp-jvm` 运行时依赖。
- 新增 `MinioClientFactory` 与 `MinioDocumentStorageStrategy`，实现对象存在性检查、读取、覆盖写入和删除。
- 将 `docker-compose.yml` 联调基线切到 `minio`，补齐 endpoint、bucket、access key、secret key 与 path-style 配置。
- 在 `DocumentStorageService` 中落实“对象先写入、元数据后创建”的补偿式建档语义，并保留 callback 失败时的旧版本对象。

## Execution Commits

- **实现提交：** `fdf0155` `feat(phase2): 落地存储策略与MinIO链路`

## Notes

- `MinioDocumentStorageStrategyTest` 已用 Testcontainers 验证 MinIO 的写入、读取、覆盖和删除四条主路径。
- 这一步没有提前实现 COS / OSS，但接口与 key 语义都保持 provider-neutral，可直接沿用。

---
*Phase: 02-storage-strategy-layer*
*Completed: 2026-03-23*
