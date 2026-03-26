# 独立部署说明

## 适用场景

当你希望把 `onlyoffice-integration-starter` 直接作为一套完整文档服务交付时，优先阅读本页。

## 部署形态

当前支持两种主形态：

- `compose demo`
  适合本地联调和快速演示
- `split deployment`
  前端、后端、ONLYOFFICE、MinIO、PostgreSQL 分开部署，适合正式环境

## 方案一：compose 聚合联调

```bash
docker compose up -d
```

默认会启动：

- `postgres`
- `minio`
- `onlyoffice`
- `server`
- `web`

默认访问入口：

```text
http://localhost:12333/
```

## 方案二：分离部署

### 后端服务

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run
```

后端正式基线：

- 数据库：PostgreSQL
- ORM：MyBatis-Flex
- 接口文档：Knife4j / OpenAPI
- 存储：MinIO 为正式默认策略，`local` 仅作开发回退

### 前端服务

```bash
cd packages/web
corepack pnpm install
corepack pnpm dev
```

### 关键地址语义

- `publicBaseUrl`
  浏览器和外部系统访问文档服务的公开地址
- `internalBaseUrl`
  ONLYOFFICE 容器访问服务端下载接口和 callback 的地址
- `documentServerUrl`
  浏览器加载 ONLYOFFICE 静态资源的地址

这 3 个地址是分布式部署正确性的关键。后端会负责生成 `document.url` 和 `callbackUrl`，前端不自行拼接运行时地址。

## 部署后建议先跑的验证

从仓库根执行：

```bash
npm run verify
```

然后再按 [验收清单](./acceptance-checklist.md) 做一次人工确认。
