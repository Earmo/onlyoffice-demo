# 最小可运行说明

## 1. 组件职责

- Vue：展示页面，调用后端拿编辑器配置
- Spring Boot：生成 ONLYOFFICE `config`，提供文件流，处理回调保存
- ONLYOFFICE Docs：真正的在线编辑器服务

## 2. 默认端口

- 前端：`5173`
- 后端：`8080`
- ONLYOFFICE Docs：`8088`

## 3. 启动顺序

### 3.1 启动 ONLYOFFICE Docs

```bash
docker compose up -d
```

默认使用固定 JWT：

```text
onlyoffice-demo-secret-2026-03-09-123456
```

### 3.2 启动 Spring Boot

```bash
cd packages/server
mvn spring-boot:run
```

后端默认会在 `packages/server/storage/demo.docx` 不存在时自动创建示例文档。

### 3.3 启动 Vue

```bash
cd packages/web
npm install
npm run dev
```

打开 `http://localhost:5173`。

## 4. 本地网络说明

这个示例把浏览器访问 ONLYOFFICE 的地址配置成：

```text
http://localhost:8088/
```

但 ONLYOFFICE 容器回调 Spring Boot、下载文档时，使用的是：

```text
http://host.docker.internal:8080
```

这对 Docker Desktop（Windows / macOS）是最省事的本地方案。

如果你在 Linux 上运行，通常需要自己覆盖后端配置，例如把：

```text
demo.onlyoffice.internal-base-url
```

改成宿主机的实际 IP，或者在 Compose 里补 `extra_hosts`。

## 5. 关键接口

- `GET /api/documents/{documentId}/editor-config`
  - 返回 ONLYOFFICE Vue 组件所需 `documentServerUrl` 和 `config`
- `GET /api/documents/{documentId}/file`
  - 给 ONLYOFFICE 下载源文件
- `POST /api/documents/{documentId}/callback`
  - 接收 ONLYOFFICE 保存回调，在 `status=2` 或 `status=6` 时拉取新文件并覆盖本地存储

## 6. 最小实现取舍

这个仓库刻意只保留最少闭环，不做这些事情：

- 用户鉴权
- 文档权限模型
- 数据库存储
- 历史版本
- 多人协同隔离
- 回调验签校验

如果要上生产，至少要继续补：

- 登录态和文档授权
- 回调请求验签
- 文档元数据持久化
- 对象存储或文件服务
- 多租户唯一 `document.key`
- HTTPS 和跨域白名单
