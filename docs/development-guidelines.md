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
- 普通 JSON API 的业务异常、参数校验异常和 JSON 解析异常统一返回 `ResponseDto`
- `BaseException` 负责携带稳定 `code`、用户可见 `message` 和 HTTP status
- `GlobalExceptionHandler` 只保留协议型异常、已提交响应的 I/O/SSE 异常和最后兜底

### 响应包装

- 普通接口统一返回 `ResponseDto<T>`
- 分页接口统一返回 `ResponseDto<PageRespVo<T>>`
- 成功响应通过 `BaseController.successResponseWithData(data)` 或 `successResponse()` 生成
- 错误响应包含 `code`、`message`，不返回异常堆栈、token、header 原文或 provider 原始报文
- 分页查询方法签名优先使用请求对象承载筛选条件，例如：

```java
ResponseDto<PageRespVo<DocumentResp>> page(@RequestBody DocumentPageReq req);
```

### 用户上下文

- 用户上下文由 `AccessContextAspect` 从请求中读取并校验，通过 `CurrentAccessContext` 保存到当前同步请求线程
- 业务入口通过 `CurrentAccessContext.getRequired()` 读取 `AccessContext`
- 同步业务方法需要租户、用户或权限字段时，优先使用 `CurrentAccessContext.tenantId()`、`actorUser()`、`actorName()`、`permissions()` 等便捷方法
- 仍消费旧 `RequestContext` 的服务可使用 `CurrentAccessContext.toRequestContext()` 平滑迁移
- controller 不再显式调用 `AccessContextResolver` 并把上下文逐层传给 service
- 线程上下文必须在请求结束时清理，避免线程复用导致用户信息串用
- SSE 或异步边界不能依赖 ThreadLocal 自动传播，应由 service 在同步入口捕获当前 `AccessContext`，并在异步回调中显式恢复再执行需要用户上下文的逻辑
- 需要在回调中临时恢复当前用户时，使用 `CurrentAccessContext.runWith(accessContext, runnable)` 或 `callWith(accessContext, supplier)`，不得手写无 `finally` 的 `set`

### 访问上下文使用规范

`CurrentAccessContext` 是唯一访问上下文 Holder。不要新增 `AccessContextHolder`、`UserContextHolder` 之类职责重复的工具类；如果需要新的读取方式，应补充到 `CurrentAccessContext` 并同步补测试。

推荐用法：

```java
String tenantId = CurrentAccessContext.tenantId();
String actorUser = CurrentAccessContext.actorUser();
```

兼容旧模型时：

```java
RequestContext requestContext = CurrentAccessContext.toRequestContext();
```

跨线程或 SSE 保活场景必须显式捕获上下文：

```java
AccessContext accessContext = CurrentAccessContext.getRequired();
executor.execute(() -> CurrentAccessContext.runWith(accessContext, () -> touch(documentId)));
```

禁止事项：

- 不要在 `CompletableFuture.runAsync`、`ScheduledExecutorService`、SSE provider stream 执行体中直接调用 `CurrentAccessContext.getRequired()`，除非该代码块外层已经显式 `runWith`
- 不要让线程池任务依赖 controller 线程结束后仍然存在的 ThreadLocal
- 不要为了普通同步 service 调用继续从 controller 层层传递 `AccessContext`
- 不要在临时绑定后遗漏 `clear` 或恢复旧上下文

### 请求参数

- 业务参数应建模为 VO/DTO，并通过 `@RequestBody` 传递
- 多参数查询使用 `POST` 请求和请求体承载条件
- 普通业务参数禁止通过 `@RequestParam`、path/query 混合或 `?documentId=xxx` 表达
- `HttpServletRequest` 只允许作为 URL 构造、协议验签或传输细节参数出现，不允许用于解析用户上下文
- 避免用 `@RequestParam` 在 URL 中拼接业务参数，例如 `?documentId=xxx`

### 接口命名

- 接口路径要显式表达动作或资源语义，例如分页查询用 `page`，列表查询用 `list`，详情查询用 `detail`
- 避免多个接口使用同名或语义含混的路径，尤其不要让删除、查询、更新等行为只靠 HTTP 方法或参数差异区分

### Phase 18 主路径示例

- `POST /api/documents/page`
- `POST /api/documents/list/recent`
- `POST /api/documents/detail`
- `POST /api/documents/create`
- `POST /api/documents/delete`
- `POST /api/document-runtime/editor-config`
- `POST /api/document-runtime/close/session`
- `POST /api/document-runtime/save`
- `POST /api/document-runtime/save-status`
- `POST /api/llm/capability/query`
- `POST /api/llm/sessions/list`
- `POST /api/llm/sessions/create`
- `POST /api/llm/sessions/detail`
- `POST /api/llm/sessions/delete`
- `POST /api/llm/sessions/rename`
- `POST /api/llm/requests/detail`
- `POST /api/llm/requests/cancel`

旧 GET、DELETE、PUT 或 query-string 入口如需保留，必须标记 `@Deprecated(forRemoval = false)`，并只作为迁移期兼容别名存在。新增测试应优先覆盖新主路径。

### 协议例外

以下端点保持原始协议形态，不包裹成普通 `ResponseDto`：

- ONLYOFFICE callback：`POST /api/document-runtime/{documentId}/callback`
- 文档二进制下载：`GET /api/document-runtime/{documentId}/file` 和 `GET /api/document-runtime/{documentId}/file.{extension}`
- 图片二进制代理：`GET /api/document-runtime/{documentId}/images/proxy`
- 文档运行态 SSE：`GET /api/document-runtime/{documentId}/runtime-events`
- LLM 流式响应：`POST /api/llm/messages/stream`
- multipart 文件字段：`POST /api/documents/upload`

协议端点如不需要业务访问上下文，必须使用 `@SkipAccessContext` 或明确测试/文档登记；SSE 建立前的同步错误仍应尽量返回统一 JSON 错误体。

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
