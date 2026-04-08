---
phase: 08-cos
plan: 03
subsystem: tencent-cos-provider-expansion
tags: [cos, storage-provider, routing, properties, docs]
requires: [08-01, 08-02]
provides:
  - 腾讯云 COS 正式 provider
  - COS 配置与路由接入统一存储模型
  - COS 回归测试与交付文档
affects: [phase-02, phase-05, phase-06]
tech-stack:
  added: [cos_api-sdk, cos-client-factory, cos-storage-strategy]
  patterns: [provider-neutral-storage, sdk-exception-translation, cloud-provider-routing]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/cos/CosClientFactory.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/cos/CosDocumentStorageStrategy.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/CosDocumentStorageStrategyTest.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageProvider.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java
    - packages/server/onlyoffice-integration-service/src/main/resources/application-dev.yml
    - packages/server/onlyoffice-integration-service/src/main/resources/application-prod.yml
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/storage/StorageProviderResolverTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationPropertiesTest.java
    - README.md
    - docs/configuration-matrix.md
    - docs/standalone-deployment.md
key-decisions:
  - "COS 继续遵守 provider-neutral 语义，上层仍然只通过 storageKey 识别对象"
  - "COS 配置块至少包含 region、bucket、secretId、secretKey 和 endpointSuffix"
  - "provider 路由优先级继续保持 tenant > sourceSystem > defaultProvider，不因新增 COS 改坏旧语义"
patterns-established:
  - "local / minio / cos 三类 provider 现在都能通过统一配置模型表达"
  - "COS SDK 异常已统一转换为 IOException，上层无需感知厂商 SDK 细节"
requirements-completed: [STOR-04]
duration: 50min
completed: 2026-03-26
---

# Phase 8 / Plan 03 Summary

**腾讯云 COS 已经成为统一存储抽象下的正式 provider，配置、路由、测试和交付文档都完成了同步闭环。**

## Accomplishments

- 新增 `StorageProvider.COS`，并落地 `CosClientFactory` 与 `CosDocumentStorageStrategy`。
- `OnlyofficeIntegrationProperties` 新增 `storage.cos` 配置块，支持 `region / bucket / secretId / secretKey / endpointSuffix`。
- `application-dev.yml` 和 `application-prod.yml` 已补 COS 配置示例与环境变量占位。
- `StorageProviderResolverTest` 新增租户和来源系统路由到 COS 的回归用例，`OnlyofficeIntegrationPropertiesTest` 也新增了 COS 绑定断言。
- README、配置矩阵和独立部署文档已经补出 `local / minio / cos` 选择方式和 COS 最小配置示例。

## Execution Notes

- COS 实现继续通过 `storageKey` 读写对象，bucket 只是底层存储容器，没有泄漏到 controller 和 service 契约中。
- 测试没有依赖真实腾讯云环境，而是通过 mock COS SDK 的交互来验证读写、删除和异常转换语义。
- 读取 COS 对象时只显式关闭对象内容流，不再额外关闭 `COSObject` 本身，避免 mock 场景下的连接释放空指针。

## Notes

- MinIO 仍然是当前正式默认 provider，COS 属于新增的正式可选 provider。
- 后续如果继续扩阿里云 OSS，可以直接沿用这次的 provider-neutral 模式与测试骨架。

---
*Phase: 08-cos*
*Completed: 2026-03-26*
