# 配置矩阵

这份矩阵只收口当前交付最关键的配置项，帮助独立部署和微服务接入快速对齐。

## Profile 选择

| Profile | 对应文件 | 默认用途 | 说明 |
|---|---|---|---|
| `dev` | `application-dev.yml` | 本地开发 | 默认 profile，允许更友好的开发默认值 |
| `test` | `application-test.yml` | 自动化测试 | 使用测试数据库和测试存储目录 |
| `prod` | `application-prod.yml` | 正式部署 | 关键地址、数据库和对象存储凭证必须显式提供 |
| `windows-debug` | `application-windows-debug.yml` | Windows 断点调试 | 叠加在 `dev` 上的覆盖层，不替代正式 profile |

统一入口文件：

```text
packages/server/onlyoffice-integration-service/src/main/resources/application.yml
```

推荐通过下面方式切换：

```text
SPRING_PROFILES_ACTIVE=dev|test|prod
```

## 核心运行地址

| 配置项 | 默认值 | 必填 | 适用场景 | 说明 |
|---|---|---|---|---|
| `ONLYOFFICE_INTEGRATION_PUBLIC_BASE_URL` | 空 | 建议 | 独立部署 / 微服务 | 浏览器或外部系统访问服务的公开地址 |
| `ONLYOFFICE_INTEGRATION_INTERNAL_BASE_URL` | `http://host.docker.internal:8080` | 是 | 分布式部署 | ONLYOFFICE 拉文件和回调使用的内部地址 |
| `ONLYOFFICE_INTEGRATION_DOCUMENT_SERVER_URL` | 空 | 建议 | 分布式部署 | 浏览器加载 ONLYOFFICE 静态资源的地址 |
| `ONLYOFFICE_INTEGRATION_DOCUMENT_SERVER_COMMAND_URL` | 空 | 否 | 分布式部署 | 服务端调用 ONLYOFFICE `CommandService.ashx` 的内部地址 |
| `ONLYOFFICE_INTEGRATION_JWT_SECRET` | 示例值 | 是 | 全部 | editor-config 和 callback JWT 的共享密钥 |
| `ONLYOFFICE_INTEGRATION_CALLBACK_JWT_HEADER_NAME` | `Authorization` | 否 | 全部 | callback JWT 默认请求头 |

## 默认上下文

| 配置项 | 默认值 | 必填 | 适用场景 | 说明 |
|---|---|---|---|---|
| `ONLYOFFICE_INTEGRATION_DEFAULT_TENANT_ID` | `native` | 否 | 开发 / 演示 | 默认租户 |
| `ONLYOFFICE_INTEGRATION_DEFAULT_SOURCE_SYSTEM` | `native` | 否 | 开发 / 演示 | 默认来源系统 |
| `ONLYOFFICE_INTEGRATION_DEFAULT_USER` | `starter-user` | 否 | 开发 / 演示 | 默认用户 ID |
| `ONLYOFFICE_INTEGRATION_DEFAULT_USER_NAME` | `默认用户` | 否 | 开发 / 演示 | 默认用户名 |

## Access Context Provider

| 配置项 | 默认值 | 必填 | 适用场景 | 说明 |
|---|---|---|---|---|
| `ONLYOFFICE_INTEGRATION_ACCESS_CONTEXT_ENABLED_PROVIDERS` | `header,jwt,default` | 否 | 全部 | 允许参与解析的 provider 名称列表 |
| `ONLYOFFICE_INTEGRATION_ACCESS_CONTEXT_RESOLUTION_ORDER` | `header,jwt,default` | 否 | 全部 | provider 实际解析顺序；前两类通常是显式策略，`default` 应放在最后作为补齐 |
| `ONLYOFFICE_INTEGRATION_ACCESS_CONTEXT_REQUIRE_EXPLICIT_CONTEXT` | `true` | 否 | 正式环境 | 完全缺失上下文时是否直接拒绝 |
| `ONLYOFFICE_INTEGRATION_ACCESS_CONTEXT_ALLOW_DEFAULT_CONTEXT` | `false` | 否 | dev/test | 是否允许使用默认值补齐缺失字段 |

### 策略语义说明

| 策略 | 类型 | 说明 |
|---|---|---|
| `header` | 显式策略 | 从请求头中解析租户、来源系统、用户和最小权限 |
| `jwt` | 显式策略 | 从 `Authorization: Bearer <token>` 中解析 claim |
| `default` | 补齐策略 | 只在允许默认补齐时提供兜底值，不表示请求真的携带了身份来源 |

推荐顺序：

```text
header -> jwt -> default
```

如果你注册了自定义 `AccessContextProvider`，通常应把它放在 `default` 之前；`default` 的职责是补齐，而不是参与“显式身份命中”的竞争。

## 存储与数据库

| 配置项 | 默认值 | 必填 | 适用场景 | 说明 |
|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:15434/onlyoffice` | 是 | 全部 | PostgreSQL 连接串 |
| `SPRING_DATASOURCE_USERNAME` | `onlyoffice` | 是 | 全部 | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `onlyoffice` | 是 | 全部 | 数据库密码 |
| `ONLYOFFICE_INTEGRATION_STORAGE_DEFAULT_PROVIDER` | `local` | 是 | 全部 | 默认存储策略，可选 `local / minio / cos` |
| `ONLYOFFICE_INTEGRATION_STORAGE_LOCAL_ROOT` | `./storage` | 否 | dev/test | 本地文件存储根目录 |
| `ONLYOFFICE_INTEGRATION_STORAGE_MINIO_ENDPOINT` | `http://localhost:9000` | MinIO 时是 | 正式环境 | MinIO endpoint |
| `ONLYOFFICE_INTEGRATION_STORAGE_MINIO_BUCKET` | `onlyoffice-documents` | MinIO 时是 | 正式环境 | MinIO bucket |
| `ONLYOFFICE_INTEGRATION_STORAGE_MINIO_ACCESS_KEY` | `onlyoffice` | MinIO 时是 | 正式环境 | MinIO Access Key |
| `ONLYOFFICE_INTEGRATION_STORAGE_MINIO_SECRET_KEY` | `onlyoffice123` | MinIO 时是 | 正式环境 | MinIO Secret Key |
| `ONLYOFFICE_INTEGRATION_STORAGE_COS_REGION` | `ap-guangzhou` | COS 时是 | 腾讯云 COS | COS 所在地域 |
| `ONLYOFFICE_INTEGRATION_STORAGE_COS_BUCKET` | 示例 bucket | COS 时是 | 腾讯云 COS | COS bucket，通常需要带 appId 后缀 |
| `ONLYOFFICE_INTEGRATION_STORAGE_COS_SECRET_ID` | 占位值 | COS 时是 | 腾讯云 COS | 腾讯云 SecretId |
| `ONLYOFFICE_INTEGRATION_STORAGE_COS_SECRET_KEY` | 占位值 | COS 时是 | 腾讯云 COS | 腾讯云 SecretKey |
| `ONLYOFFICE_INTEGRATION_STORAGE_COS_ENDPOINT_SUFFIX` | `cos.myqcloud.com` | 否 | 腾讯云 COS | COS 域名后缀，私有域名场景可覆盖 |

### provider 选择建议

| provider | 推荐场景 | 说明 |
|---|---|---|
| `local` | 本地开发 / 自动化测试 | 无需依赖对象存储，便于快速启动 |
| `minio` | 私有化部署 / 默认正式基线 | 当前仓库默认正式对象存储路径 |
| `cos` | 腾讯云环境 | 适合直接把文档对象放到腾讯云 COS |

当前正式支持可以概括为：

```text
local / minio / cos
```

## 官方前端页面入口

| 路由 | 页面意图 | 说明 |
|---|---|---|
| `/` | 文档工作台 | 文档列表、搜索筛选、新建、上传、远程导入 |
| `/preview/{documentId}` | 只读预览 | 只查看文件，不建立活跃编辑会话 |
| `/editor/{documentId}` | 编辑工作台 | 进入可编辑模式，并在离开页面时显式结束编辑会话 |

## 远程资源安全

| 配置项 | 默认值 | 必填 | 适用场景 | 说明 |
|---|---|---|---|---|
| `ONLYOFFICE_INTEGRATION_REMOTE_RESOURCE_MAX_DOCUMENT_BYTES` | `52428800` | 否 | 全部 | 远程导入文档大小上限 |
| `ONLYOFFICE_INTEGRATION_REMOTE_RESOURCE_MAX_IMAGE_BYTES` | `10485760` | 否 | 全部 | 图片代理大小上限 |
| `ONLYOFFICE_INTEGRATION_REMOTE_RESOURCE_ALLOW_PRIVATE_ADDRESS_ACCESS` | `false` | 否 | 受控联调 | 是否允许访问私网地址，生产应保持关闭 |

## Compose 相关

`.env.example` 里当前提供了最常用的 2 个对外变量：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `WEB_PORT` | `80` | 聚合前端对外端口 |
| `JWT_SECRET` | 示例值 | ONLYOFFICE 与服务端共享 JWT 密钥 |

## Windows 本地调试约定

如果使用 `docker-compose.debug.yml` 启动基础依赖，并把后端放在 Windows 本机调试，当前约定如下：

| 调试项 | 值 | 说明 |
|---|---|---|
| PostgreSQL | `localhost:15434` | 本机后端连接容器数据库 |
| MinIO API | `http://localhost:9000` | 本机后端连接容器 MinIO |
| MinIO Console | `http://localhost:9001` | 浏览器查看对象存储 |
| ONLYOFFICE Docs | `http://localhost:18080` | 浏览器加载 ONLYOFFICE 静态资源 |
| 本机后端回调入口 | `http://host.docker.internal:8080` | ONLYOFFICE 容器回调 Windows 本机后端 |

推荐直接启用：

```text
spring.profiles.active=windows-debug
```

对应 YAML 配置文件：

```text
packages/server/onlyoffice-integration-service/src/main/resources/application-windows-debug.yml
```
