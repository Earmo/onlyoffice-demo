# 文档服务最小接入说明

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
- `onlyoffice`
- `server`
- `web`

对外统一入口默认是：

```text
http://localhost:12333/
```

### 3.2 单独启动后端

```bash
cd packages/server
mvn spring-boot:run
```

后端默认使用本地 H2 作为开发环境元数据存储；在 compose demo 中会切到 PostgreSQL。

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
   显式创建原生文档，或建立需要编辑的文档上下文
2. 或调用 `POST /api/documents/upload`
   上传本地文档并生成内部 `documentId`
3. 或调用 `POST /api/documents/import-remote`
   导入网络文档并生成内部 `documentId`
4. 调用 `GET /api/documents/{documentId}/editor-config`
   获取 ONLYOFFICE 初始化配置
5. 再决定：
   - 跳转/嵌入官方前端
   - 或由上游系统自己的前端消费该配置

## 5. 核心 API

- `GET /api/documents`
  - 按当前 `tenantId` 返回文档列表
- `GET /api/documents/{documentId}`
  - 返回文档详情
- `POST /api/documents`
  - 显式创建文档，不会隐式 auto-create
- `POST /api/documents/upload`
  - 上传本地文档并返回内部 `documentId`
- `POST /api/documents/import-remote`
  - 导入网络文档并返回内部 `documentId`
- `GET /api/documents/{documentId}/editor-config`
  - 返回 ONLYOFFICE Vue 组件所需 `documentServerUrl` 和 `config`
- `GET /api/documents/{documentId}/file`
  - 给 ONLYOFFICE 拉取源文件
- `POST /api/documents/{documentId}/callback`
  - 接收 ONLYOFFICE 保存回调并更新共享元数据状态

## 6. 用户上下文透传

v1 以服务到服务透传为主，默认支持这些请求头：

- `X-Tenant-Id`
- `X-Source-System`
- `X-External-User-Id`
- `X-User-Display-Name`

如果上游系统未透传，服务会回退到 demo 默认值。`tenantId`、`sourceSystem` 和当前用户都会进入文档元数据或编辑配置，而不是写死在文档核心逻辑里。

## 7. 现阶段取舍

这个仓库现在已经具备：

- 共享元数据持久化基础
- 官方前端聚合入口
- API-first 的文档创建 / 上传 / 导入 / 编辑配置边界

但还没有完成：

- MinIO / COS / OSS 正式存储策略
- 完整用户认证体系接入
- 首页文档列表 UI 重构
- 分布式 callback 安全校验和更细的保存状态审计
