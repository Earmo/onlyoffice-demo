# onlyoffice-integration-starter

这是一个面向“独立软件服务 + 可嵌入微服务”的 ONLYOFFICE 在线文档编辑服务基线。仓库已经具备：

- `packages/server/onlyoffice-integration-data`
  数据库实体、repository、Flyway 迁移和持久化边界
- `packages/server/onlyoffice-integration-service`
  Spring Boot 服务，负责文档 API、ONLYOFFICE editor-config、文件流、callback 和安全边界
- `packages/web`
  Vue 3 官方前端工作台，提供文档列表、创建入口和独立编辑页
- `docker-compose.yml`
  本地联调用的聚合部署方式

默认聚合入口：

```text
http://localhost:12333/
```

## 统一验证入口

仓库根已经提供分层验证命令，默认推荐直接从根目录执行：

```bash
npm run verify
```

它会依次执行：

1. 后端测试：`npm run test:server`
2. 前端测试：`npm run test:web`
3. 前端构建：`npm run build:web`
4. compose 配置校验：`npm run verify:compose`

如果你在排查问题，也可以单独运行这些分层命令，而不需要手工拼接子模块命令。

## 本地开发与联调

### 后端 Profile 约定

后端配置已经按正式环境拆成 YAML profile：

- `dev`
  日常开发默认 profile，本地 PostgreSQL / local provider 友好
- `test`
  自动化测试 profile，走测试数据库与测试存储根目录
- `prod`
  生产部署 profile，关键地址和对象存储凭证必须显式提供
- `windows-debug`
  叠加在 `dev` 之上的 Windows 本地断点调试覆盖层

默认入口文件是：

```text
packages/server/onlyoffice-integration-service/src/main/resources/application.yml
```

默认 profile 选择方式：

```bash
SPRING_PROFILES_ACTIVE=dev
```

### Compose 聚合联调

```bash
docker compose up -d
```

默认 compose 会把 `web` 监听在 `0.0.0.0:${WEB_PORT:-12333}`，并默认把公开入口固定为 `http://172.18.109.7:12333`。如果你需要换成自己的局域网 IP、公网域名或反向代理地址，再覆盖下面两个变量：

```bash
$env:ONLYOFFICE_INTEGRATION_PUBLIC_BASE_URL="https://你的公网域名或IP:端口"
$env:ONLYOFFICE_INTEGRATION_DOCUMENT_SERVER_URL="https://你的公网域名或IP:端口/api/office"
$env:WEB_BIND_IP="0.0.0.0"
docker compose up -d
```

例如临时切回本机：

```bash
$env:ONLYOFFICE_INTEGRATION_PUBLIC_BASE_URL="http://localhost:12333"
$env:ONLYOFFICE_INTEGRATION_DOCUMENT_SERVER_URL="http://localhost:12333/api/office"
docker compose up -d
```

当前默认会把 ONLYOFFICE 浏览器侧资源挂在同源前缀：

```text
http://172.18.109.7:12333/api/office/
```

也就是说，编辑器里的版本化地址会类似：

```text
http://172.18.109.7:12333/api/office/9.3.1-xxxx/doc/<documentKey>/c/...
```

### Windows 本地断点调试

如果你在 Windows + Docker Desktop 环境下，想让 `postgres`、`minio`、`onlyoffice` 继续跑在容器里，但把后端和前端放到本机 IDE / 终端里启动，推荐使用调试覆盖文件：

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d postgres minio onlyoffice
```

这条命令会在不改动主 compose 语义的前提下，额外暴露本机调试需要的端口：

- `localhost:15434` -> PostgreSQL
- `localhost:9000` / `localhost:9001` -> MinIO API / Console
- `localhost:18080` -> ONLYOFFICE Docs

后端本机启动时，建议直接启用新增的 YAML profile：

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run -Dspring-boot.run.profiles=windows-debug
```

前端本机启动时，默认会通过 Vite 代理把 `/api` 转发到本机后端 `http://localhost:8080`：

```bash
cd packages/web
corepack pnpm install
corepack pnpm dev
```

如果你需要改成别的后端地址，再覆盖代理目标即可：

```bash
$env:VITE_DEV_API_PROXY_TARGET="http://localhost:8081"
corepack pnpm dev
```

如果要确认 ONLYOFFICE 容器能否打回你本机后端，可以执行：

```bash
docker exec -it onlyoffice-integration-docs curl http://host.docker.internal:8080/v3/api-docs
```

### 单独启动后端

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run
```

### 单独启动前端

```bash
cd packages/web
corepack pnpm install
corepack pnpm dev
```

## 文档导航

- [最小接入说明](docs/minimal-integration.md)
- [交付总览](docs/delivery-overview.md)
- [开发规范](docs/development-guidelines.md)
- [独立部署说明](docs/standalone-deployment.md)
- [微服务接入说明](docs/microservice-integration.md)
- [配置矩阵](docs/configuration-matrix.md)
- [验收清单](docs/acceptance-checklist.md)

## 推荐阅读顺序

第一次接手这个仓库时，建议按下面顺序理解和验证：

1. 先看本文，了解仓库结构和根级验证命令
2. 执行 `npm run verify`，确认本地基线可通过
3. 如果要独立部署，继续看 [独立部署说明](docs/standalone-deployment.md)
4. 如果要把它接入上游业务系统，继续看 [微服务接入说明](docs/microservice-integration.md)
5. 需要对环境变量做细化时，查 [配置矩阵](docs/configuration-matrix.md)
6. 交付前按 [验收清单](docs/acceptance-checklist.md) 做一次人工核对

## 存储 Provider

当前统一存储抽象已经正式支持 3 类 provider：

- `local`
  适合开发和测试环境，方便本地快速起服务
- `minio`
  当前默认的正式对象存储基线
- `cos`
  腾讯云 COS provider，适合直接接入腾讯云对象存储

切换方式统一通过：

```bash
ONLYOFFICE_INTEGRATION_STORAGE_DEFAULT_PROVIDER=local|minio|cos
```

如需按租户或来源系统路由到不同 provider，可继续配置：

```text
onlyoffice.integration.storage.routing.tenants.*
onlyoffice.integration.storage.routing.source-systems.*
```
