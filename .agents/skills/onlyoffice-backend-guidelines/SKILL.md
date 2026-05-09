---
name: onlyoffice-backend-guidelines
description: ONLYOFFICE 后端详细规范。修改 packages/server 下 Java/Spring Boot 后端、数据模块、API、服务、配置、测试、日志、安全或构建命令时优先使用。
---

# ONLYOFFICE 后端详细规范

## 适用范围

后端代码位于：

```text
packages/server
```

模块边界：

- `onlyoffice-integration-data`：数据库 Entity、Mapper、Repository、Flyway migration。
- `onlyoffice-integration-service`：Spring Boot 应用、Controller、Service、配置、AccessContext、ONLYOFFICE、存储、LLM。

处理后端任务时，优先读取本 skill；涉及具体功能时再按需读取：

- `onlyoffice-backend-api`
- `onlyoffice-data-migrations`
- `onlyoffice-storage-providers`
- `onlyoffice-editor-security`
- `onlyoffice-delivery-observability`

## 目录职责

- `controller`：HTTP API、ONLYOFFICE callback、文件流、LLM API 入口。
- `service` / `service.impl`：业务编排、状态流转、外部依赖调用。
- `context`：租户、来源系统、用户、权限等访问上下文。
- `storage`：存储抽象和 provider 策略。
- `data.entity`、`data.mapper`、`data.repository`：持久层。
- `model.request` / `model.response`：请求响应模型。
- `common.exception`、`exception`：异常模型和错误收口。
- `src/test/java`：后端测试。

## 编码规范

- Java 代码保持明确类型，不使用晦涩缩写。
- Controller 不直接访问 Mapper；通过 Service/Repository 分层。
- 请求响应统一使用 `ResponseDto<T>`、`PageRespVo<T>` 或明确的 response model。
- API 输入模型放在 `model.request`，输出模型放在 `model.response`。
- 业务状态变更集中在 Service，避免 Controller 承载业务流程。
- 新增配置项要进入 `OnlyofficeIntegrationProperties` 或对应 properties 类，并同步 `.env.example` 与 docs。
- 不在代码中硬编码真实密钥、token、bucket 凭据、Document Server 地址。

## API 文档注释

- 新增或修改后端 API 时，Controller 接口方法必须补齐 Swagger/OpenAPI 注解，至少包含 `@Operation`；路径参数、查询参数、请求体参数按场景补 `@Parameter`、`@RequestBody` 说明或可被 schema 展示的类型注解。
- 接口相关的请求/响应 model、DTO、VO、Entity 类名必须同时有类级 Javadoc 和 Swagger `@Schema(description = "...")`。
- 接口相关字段必须同时有 Javadoc 能表达的语义说明，以及 Swagger `@Schema(description = "...")`；普通类字段使用字段 Javadoc，Java record 组件使用类级 Javadoc 的 `@param` 标签，并在组件上使用 `@Schema`。
- 参数说明要写业务含义、允许值或边界。例如状态筛选、排序方向、页码起点、每页最大条数等，不要只复述字段名。
- 修改既有接口字段时，同步更新 Javadoc、Swagger 注解、测试断言和前端调用方使用的字段语义。

## 访问上下文

- 新增业务 API 默认接入 `AccessContext`。
- 仅健康检查、公开 callback 或明确无需上下文的端点使用 `@SkipAccessContext`。
- 业务查询、文档元数据、LLM 会话、审计记录必须考虑租户、来源系统和用户作用域。
- 访问上下文异常使用既有异常类型，并由全局异常处理收口。

## 数据与事务

- 新表通过 Flyway migration 创建。
- migration 使用连续版本号：`V{number}__description.sql`。
- 新增表同步 Entity、Mapper、Repository 和必要测试。
- Service 写操作需要考虑事务边界，尤其是文档元数据、编辑会话、运行事件、审计事件、LLM 消息多表联动。
- 删除/关闭/保存状态类操作要处理并发冲突和重复请求。

## 日志规范

后端必须补关键 `info` 级业务日志，不只在异常分支打日志。

至少覆盖：

- 请求或任务开始。
- 创建、导入、删除、打开编辑、关闭会话、保存、回调、取消、重试、超时等状态迁移。
- 调用对象存储、ONLYOFFICE Document Server、LLM provider 等外部依赖的开始和结束。
- SSE/流式响应打开、关闭、中断、完成。

日志字段优先包含：

- `documentId`
- `documentKey`
- `sessionId`
- `requestId`
- `tenantId`
- `sourceSystem`
- `userId`
- `provider`
- `model`

禁止在 `info` 日志输出密钥、令牌、完整文档正文、完整 LLM prompt/response 或 token 级高频内容。高频细节只能放在 `debug`，并默认关闭。

## 后端测试

后端测试命令：

```powershell
npm run test:server
mvn -f packages/server/pom.xml test
```

新增或修改后端行为时：

- Controller 行为补 Controller test。
- AccessContext 改动补上下文解析、缺失、非法权限测试。
- 数据层改动补 Repository/Service 相关测试。
- ONLYOFFICE callback、保存状态、编辑会话、并发冲突要补回归测试。
- LLM 流式、取消、provider 错误要覆盖正常和失败路径。

## 后端交付检查

- 后端测试通过。
- 相关配置已同步 `.env.example`、`docs/configuration-matrix.md` 或部署文档。
- migration、Entity、Repository、Service 保持一致。
- 关键业务日志已补齐。
- 未泄露密钥、token、敏感正文。
- API 响应结构与前端解析保持一致。
