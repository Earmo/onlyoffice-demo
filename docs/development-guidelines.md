# 开发规范

本文档记录仓库内默认遵循的开发约束。新增功能、修复缺陷和重构代码时，默认按本文执行，除非需求文档明确说明例外。

## 日志约定

当前仓库的代码生成与人工开发都必须重视可观测性，不能只依赖异常堆栈排障。业务代码默认应主动输出足够的 `info` 级日志，确保问题发生后能用同一组标识串起完整链路。

### 必须打印 `info` 日志的关键节点

- 请求、任务或异步流程的开始与结束
- 关键状态迁移，例如 `pending -> in_progress -> completed/failed/cancelled`
- 外部依赖调用的开始、成功、失败与超时
- 重试、取消、降级、回退、忽略过期结果等关键分支
- 流式链路的重要收口点，例如 SSE 打开、客户端断开、服务端取消、上游停止接收响应

### 日志字段要求

日志应优先携带稳定且可检索的上下文字段，例如：

- `documentId`
- `sessionId`
- `requestId`
- `provider`
- `model`
- `tenantId`
- `actorUser`

如果一次流程跨越前端、服务端、异步任务和第三方接口，至少要保证其中一个核心标识可以贯穿整条链路。

### 禁止事项

- 不要在 `info` 日志中打印密钥、令牌、Cookie、完整 prompt、完整文档正文或其他敏感原文
- 不要把高频循环、token 逐条输出、心跳明细直接打到 `info`
- 不要用无法检索的自然语言替代结构化字段

### 推荐做法

- 开始日志说明“要做什么”，结束日志说明“结果如何”
- 外部调用日志同时打印目标系统和本地关联 ID
- 遇到取消、超时、断连这类灰色状态时，也要有明确 `info` 或 `warn` 日志，不要只在最终报错时留下痕迹
- 对同一条业务链路，日志命名和字段名保持一致，方便全文检索

## 变更原则

- 修复问题时，除了修业务逻辑，也要补足对应链路的观测点
- 新增异步、流式、重试、补偿逻辑时，必须同步设计日志节点
- 如果某处故意不打日志，应能说明原因，例如高频热路径或敏感数据约束

## Java 后端接口契约

`packages/server/onlyoffice-integration-service` 的接口层默认遵循统一 controller、异常、响应和用户上下文约定。新增接口和改造既有接口时，优先消除 controller 内的上下文解析、裸响应和 URL 参数拼接。

### Controller 与异常

- `controller` 包下的 controller 类统一继承 `BaseController`
- 业务异常类统一继承 `BaseException`
- 异常处理和前端响应包装通过 `BaseController` 定义的切面统一完成，controller 不重复编写分散的异常响应逻辑

### 响应包装

- 普通接口统一返回 `ResponseDto<T>`
- 分页接口统一返回 `ResponseDto<PageRespVo<T>>`
- 分页查询方法签名优先使用请求对象承载筛选条件，例如：

```java
ResponseDto<PageRespVo<DocumentResp>> page(@RequestBody DocumentPageReq req);
```

### 用户上下文

- 用户上下文由切面从请求中读取并校验，通过 `ThreadLocal` 或等价线程隔离机制保存到当前线程
- 提供工具类从当前线程读取用户上下文，业务服务通过工具类获取用户信息
- controller 不再显式调用 `AccessContextResolver` 并把上下文逐层传给 service
- 线程上下文必须在请求结束时清理，避免线程复用导致用户信息串用

### 请求参数

- 业务参数应建模为 VO/DTO，并通过 `@RequestBody` 传递
- 多参数查询使用 `POST` 请求和请求体承载条件
- 避免在业务代码中直接依赖 `HttpServletRequest`
- 避免用 `@RequestParam` 在 URL 中拼接业务参数，例如 `?documentId=xxx`

### 接口命名

- 接口路径要显式表达动作或资源语义，例如 `delete/session`、`get/session`
- 避免多个接口使用同名或语义含混的路径，尤其不要让删除、查询、更新等行为只靠 HTTP 方法或参数差异区分

## Java 注释约定

Java 代码统一使用标准 Javadoc 格式。字段注释可以使用单行 Javadoc；方法注释必须包含用途说明，并按签名补齐 `@param`、`@return`，会主动抛出业务异常或受检异常时补 `@throws`。

### 字段注释

```java
/** 用户登录的唯一标识，通常为手机号或邮箱地址，创建后不可修改。 */
private String username;
```

### 方法注释

```java
/**
 * 自动创建消息（根据业绩和规则）。
 *
 * @param performance 业绩信息。
 * @param ruleId 规则 ID。
 * @return 消息 DTO。
 */
TtsWecomMessageDto createAutoMessage(TtsContractPerformanceDto performance, String ruleId);
```

### 书写要求

- `@param` 名称必须与方法签名中的参数名一致
- 有返回值的方法必须写 `@return`，`void` 方法不写 `@return`
- 描述要说明业务语义，不要只重复类型名
- 复杂方法可在正文中补“处理步骤”，但标签仍要齐全
