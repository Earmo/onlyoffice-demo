---
name: onlyoffice-storage-providers
description: ONLYOFFICE 存储 Provider 规则。修改 DocumentStorageService、StorageProviderResolver、local/minio/cos 存储策略、对象 key、文件流、配置路由或存储环境变量时使用。
---

# ONLYOFFICE 存储 Provider 规则

## 存储抽象

存储相关代码位于：

```text
packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage
```

核心类型：

- `DocumentStorageStrategy`
- `DocumentStorageService`
- `StorageProviderResolver`
- `StorageProvider`
- `StorageKeyFactory`
- `StorageWriteRequest`
- `StoredObjectResource`

## Provider

当前支持：

- `local`：开发和测试环境，使用本地文件根目录。
- `minio`：默认正式对象存储基线。
- `cos`：腾讯云 COS provider。

切换方式：

```text
ONLYOFFICE_INTEGRATION_STORAGE_DEFAULT_PROVIDER=local|minio|cos
```

按租户或来源系统路由：

```text
onlyoffice.integration.storage.routing.tenants.*
onlyoffice.integration.storage.routing.source-systems.*
```

## 实现规则

- 新 provider 必须实现 `DocumentStorageStrategy`，并接入 `StorageProviderResolver`。
- 对象 key 统一通过 `StorageKeyFactory` 生成，不要在业务代码里散落拼接规则。
- 文档内容读写通过 `DocumentStorageService`。
- 写入请求使用 `StorageWriteRequest` 表达内容、元数据和目标信息。
- 不要在日志中输出对象存储密钥或完整敏感文件内容。

## Compose 默认值

Compose 默认使用 `minio`：

- endpoint: `http://minio:9000`
- bucket: `onlyoffice-documents`
- access key: `onlyoffice`
- secret key: `onlyoffice123`

真实环境必须通过环境变量覆盖凭据。
