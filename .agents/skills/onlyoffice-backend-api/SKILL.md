---
name: onlyoffice-backend-api
description: ONLYOFFICE 后端 API 和服务规则。修改 Spring Boot Controller、文档 API、LLM API、ResponseDto、异常处理、AccessContext 或后端服务逻辑时使用。
---

# ONLYOFFICE 后端 API 规则

## 后端模块

后端位于：

```text
packages/server
```

主要模块：

- `onlyoffice-integration-data`：Entity、Mapper、Repository、Flyway。
- `onlyoffice-integration-service`：应用入口、Controller、Service、配置、安全上下文、存储、ONLYOFFICE、LLM。

## Controller

主要 Controller：

- `DocumentApiController`：文档列表、创建、导入、删除、保存状态、会话等业务 API。
- `DocumentController`：ONLYOFFICE 编辑相关端点、文件流、callback、editor config。
- `LlmController`：编辑器 AI 工作台的会话、消息、流式响应、取消等接口。

统一响应模型：

- `ResponseDto<T>`
- `PageRespVo<T>`
- `ApiErrorResponse`

Swagger 与 Javadoc：

- Controller 新增或修改接口时，方法必须补 `@Operation`；路径、查询、请求体等入参按场景补 `@Parameter`、`@RequestBody` 或可展示的 schema 注解。
- 接口相关请求/响应 model、DTO、VO、Entity 必须有类级 Javadoc 与 `@Schema(description = "...")`。
- 接口相关字段或 record 组件必须有清晰业务说明和 `@Schema(description = "...")`，必要时写明允许值、默认值、页码起点、数量上限等边界；普通类字段使用字段 Javadoc，Java record 组件使用类级 Javadoc 的 `@param` 标签。

异常处理：

- 业务异常继承或使用 `BaseException`、`CommonException`。
- 全局异常通过 `GlobalExceptionHandler` 收口。
- 文档相关异常包括 `DocumentNotFoundException`、`DocumentOperationConflictException`。
- 访问上下文异常包括 `MissingAccessContextException`、`InvalidAccessContextException`。

## AccessContext

多租户/来源/用户上下文由 `context` 包处理：

- `AccessContext`
- `CurrentAccessContext`
- `AccessContextResolver`
- `HeaderAccessContextProvider`
- `JwtAccessContextProvider`
- `DefaultAccessContextProvider`
- `AccessContextAspect`
- `SkipAccessContext`

新增 API 默认应接入访问上下文，只有公开回调、健康检查或明确无需上下文的端点才使用跳过机制。

## 日志

新增或修改业务代码时补充 `info` 级关键业务日志，覆盖：

- 请求或任务开始。
- 创建、取消、完成、回退、重试、超时等状态迁移。
- 数据库外部的 HTTP、对象存储、ONLYOFFICE、LLM provider 调用开始和结束。
- 流式响应打开、关闭、中断、完成。

日志优先包含 `documentId`、`sessionId`、`requestId`、`provider`、`model`、租户或用户作用域。不要在 info 日志输出密钥、令牌、完整敏感正文或 token 级高频噪音。
