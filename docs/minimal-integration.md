# onlyoffice-integration-starter 最小接入说明

## 1. 服务角色

- `api service`
  负责文档主数据、创建/上传/导入、ONLYOFFICE editor config、文件流和 callback
- `official web`
  官方前端客户端，默认通过 nginx 聚合同域访问 `/api` 与 ONLYOFFICE 资源
- `compose demo`
  便于本地联调的聚合部署形态，不代表唯一生产部署方式

## 2. 地址语义

- `publicBaseUrl`
  浏览器、外部系统或跳转入口使用的公开服务地址
- `internalBaseUrl`
  ONLYOFFICE 容器拉取文件、插图代理、保存回调时访问 API 的内部地址
- `documentServerUrl`
  浏览器加载 ONLYOFFICE 静态资源时使用的地址；在聚合部署场景下可复用官方前端入口

`editor-config`、`file`、`callback` 的绝对地址都由后端生成，前端不需要自行拼接。

## 3. 最小部署

### 3.1 Compose demo

```bash
docker compose up -d
```

当前 compose demo 会启动：

- `postgres`
- `minio`
- `onlyoffice`
- `server`
- `web`

对外统一入口默认是：

```text
http://localhost:12333/
```

### 3.2 单独启动后端 starter

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run
```

后端当前以 PostgreSQL 作为正式数据库基线；测试环境仍使用 H2 内存库辅助自动化测试。

## 3.4 存储基线

- 正式联调和默认服务基线使用 `minio`
- `local` 仅保留给 `dev/test` 或迁移过渡场景
- 存储对象键统一为 `tenant/sourceSystem/documentId.ext`
- `tenant` 路由优先级高于 `sourceSystem`，都未命中时回退到默认 provider
- 未来接入腾讯云 `COS` 或阿里云 `OSS` 时，只需要新增 provider 实现和配置映射，不需要改 controller 或上层 API 流程

创建、上传、导入文档时，服务会先写对象再落元数据；如果元数据创建失败，会尽量回滚对象写入，避免留下半成品文档。ONLYOFFICE callback 回写失败时，会保留最近一次成功版本，并把文档主状态标记为 `failed`。

### 3.3 单独启动官方前端

```bash
cd packages/web
pnpm install
pnpm dev
```

## 4. 推荐接入流程

上游系统应先调用服务端 API 建立文档上下文，再拿到内部 `documentId`。不要直接假定“打开文档”会自动创建文档，系统不会隐式 auto-create。

推荐顺序：

1. 调用 `POST /api/documents`
2. 或调用 `POST /api/documents/upload`
3. 或调用 `POST /api/documents/import-remote`
4. 调用 `GET /api/documents/{documentId}/editor-config`
5. 再决定跳转官方前端，或由上游系统自己的前端消费该配置

## 5. 核心 API

- `GET /doc.html`
- `GET /v3/api-docs`
- `GET /api/documents`
- `GET /api/documents/{documentId}`
- `POST /api/documents`
- `POST /api/documents/upload`
- `POST /api/documents/import-remote`
- `GET /api/documents/{documentId}/editor-config`
- `GET /api/documents/{documentId}/file`
- `POST /api/documents/{documentId}/callback`

## 6. 用户上下文透传

用户上下文解析遵循 `SPI-first, built-ins included`：

- 官方内置 `header`、`jwt`、`default` 三种 provider
- 也支持注册自定义 provider，并通过 `enabled-providers` / `resolution-order` 参与解析
- 默认 provider 顺序是 `header,jwt,default`

### 6.1 Header provider

默认支持这些请求头：

- `X-Tenant-Id`
- `X-Source-System`
- `X-External-User-Id`
- `X-User-Display-Name`
- `X-Access-Permissions`

其中 `X-Access-Permissions` 的最小格式为：

```text
edit=true,comment=false,download=true,print=false
```

当前 Phase 3 只消费 `edit/comment/download/print` 这 4 个最小权限，不扩成完整权限系统。

### 6.2 JWT provider

- 默认从 `Authorization` 头读取 `Bearer Token`
- claim 映射通过 `onlyoffice.integration.access-context.jwt.claim-mappings.*` 配置
- `permissions` claim 支持字符串格式或对象格式

### 6.3 严格模式与默认补齐

- 完全缺失用户上下文时，接口会返回 `4xx`
- 只有显式开启 `allow-default-context=true` 时，才允许补齐缺失字段
- 建议只在 `dev/test` profile 开启默认补齐

### 6.4 自定义 provider 扩展

如果要接外部用户中心，只需要：

1. 新增一个 `AccessContextProvider` Bean
2. 让 `name()` 返回稳定名称
3. 把该名称加入 `enabled-providers` 和 `resolution-order`

不需要修改 controller、文档业务 API 或 ONLYOFFICE editor-config 调用链。

### 6.5 审计语义

- create/upload/import/editor-config 会记录轻量访问审计事件
- ONLYOFFICE callback 会记录为 `system event`
- callback 不会被伪装成某个人类用户的直接保存动作
