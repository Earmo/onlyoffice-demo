# onlyoffice-integration-starter 最小接入说明

这份文档保持“最小接入说明”定位，只回答最短路径怎么接。更完整的交付材料请看：

- [交付总览](./delivery-overview.md)
- [独立部署说明](./standalone-deployment.md)
- [微服务接入说明](./microservice-integration.md)
- [配置矩阵](./configuration-matrix.md)
- [验收清单](./acceptance-checklist.md)

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

Phase 5 开始，`documentServerUrl` 和 `internalBaseUrl` 不再允许由运行时请求静默推导；如果配置缺失或不是合法的 `http/https` 地址，服务会尽早返回明确错误，避免生成错误的 ONLYOFFICE 运行时链接。

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
corepack pnpm install
corepack pnpm dev
```

## 4. 推荐接入流程

上游系统应先调用服务端 API 建立文档上下文，再拿到内部 `documentId`。不要直接假定“打开文档”会自动创建文档，系统不会隐式 auto-create。

推荐顺序：

1. 调用 `POST /api/documents`
2. 或调用 `POST /api/documents/upload`
3. 或调用 `POST /api/documents/import-remote`
4. 调用 `GET /api/documents/{documentId}/editor-config`
5. 再决定跳转官方前端，或由上游系统自己的前端消费该配置

### 4.1 官方前端默认流转

官方前端现在默认先进入文档工作台，而不是直接打开固定示例文档：

- `GET /`
  文档工作台首页，展示当前租户/当前用户上下文、顶部创建入口、最近文档区和主列表
- `GET /editor/{documentId}`
  独立编辑页，负责加载 ONLYOFFICE 编辑器、保存状态和文档切换入口

工作台首页默认支持三类主动作：

- `新建空白文档`
- `上传本地文档`
- `导入远程文档`

三类动作成功后，官方前端会先刷新 `/api/documents` 列表并高亮新结果，而不是强制立即跳转编辑页。这样可以避免新文档被当前筛选条件静默隐藏，也让用户在进入编辑器前先确认列表结果。

从工作台进入编辑器后：

- 列表项整行可进入文档编辑页
- 编辑页始终提供“返回文档列表”入口
- 在编辑页切换到另一份文档时，前端会先弹出确认提示

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

### 5.1 ONLYOFFICE callback JWT

- callback 以 JWT 验签为主可信边界
- 默认沿用 `onlyoffice.integration.jwt-secret`
- 默认从 `Authorization` 请求头读取 callback token，可通过 `onlyoffice.integration.callback.jwt-header-name` 调整
- 验签失败时，接口会返回明确 `4xx`，并记录 `callback_rejected` 运行事件与 system audit event

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

## 7. 远程资源安全边界

- 远程导入和图片代理统一使用同一套 SSRF 防护
- 默认拒绝内网、回环、本机保留地址段
- 文档导入和图片代理都带显式大小上限
- 文档导入执行“扩展名 + Content-Type”双校验
- 图片代理要求响应 `Content-Type` 必须是 `image/*`
- 被安全策略拒绝时，接口会返回明确 `4xx + 可读错误信息`

关键配置：

- `onlyoffice.integration.remote-resource.max-document-bytes`
- `onlyoffice.integration.remote-resource.max-image-bytes`
- `onlyoffice.integration.remote-resource.allow-private-address-access`

其中 `allow-private-address-access` 只建议在本地测试或受控联调环境临时开启，生产环境应保持默认关闭。
