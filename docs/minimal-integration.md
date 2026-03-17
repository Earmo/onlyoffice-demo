# 最小可运行说明

## 1. 组件职责

- Vue：展示页面，调用后端拿编辑器配置
- Spring Boot：生成 ONLYOFFICE `config`，提供文件流，处理回调保存
- ONLYOFFICE Docs：真正的在线编辑器服务

## 2. 默认端口

- 对外统一入口：`12333`
- 容器内 Spring Boot：`8080`
- 容器内 nginx：`80`
- 容器内 ONLYOFFICE Docs：`80`

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

在 Docker 一体化部署场景下，这个示例把浏览器访问 ONLYOFFICE 的地址配置成“当前访问站点本身”：

```text
http://<当前访问域名>:<WEB_PORT>/
```

浏览器访问页面、调用 API、加载 ONLYOFFICE 静态资源时，统一走 nginx 同源反代。

这意味着：

- 本机访问时可以直接打开 `http://localhost:12333/`
- 局域网访问时可以直接打开 `http://你的局域网IP:12333/`
- 公网动态 IP 或域名访问时，也不需要再修改后端 `document-server-url`

ONLYOFFICE 容器下载文档、插图、回调 Spring Boot 时，使用的是：

```text
http://web:80
```

这样可以避免浏览器走一个地址、容器再走另一套 `localhost` / 宿主机地址，导致下载失败或连接被拒绝。

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
