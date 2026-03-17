# Architecture

**Analysis Date:** 2026-03-17

## Pattern Overview

**Overall:** Docker-compose 驱动的前后端分离最小集成示例

**Key Characteristics:**
- 单体 Spring Boot API 负责全部业务逻辑与 ONLYOFFICE 协议收敛
- Vue 前端只保留轻量宿主页面与控制台，不直接拼装复杂编辑器配置
- nginx 作为统一浏览器入口，把静态资源、`/api/` 与 ONLYOFFICE 路径收敛到同源
- 本地文件系统代替数据库和对象存储，强调“最小可运行闭环”

## Layers

**Browser UI Layer:**
- Purpose: 展示宿主页、加载编辑器、发起上传/导入/插图/切换模式动作
- Contains: `packages/web/src/App.vue`、`packages/web/src/main.js`、`packages/web/src/style.css`
- Depends on: 后端 `/api/documents/*` 接口和 ONLYOFFICE Vue SDK
- Used by: 终端用户浏览器

**HTTP Edge Layer:**
- Purpose: 统一浏览器访问入口，隐藏后端和 ONLYOFFICE 的内部地址差异
- Contains: `packages/web/nginx.conf`
- Depends on: `server:8080` 与 `onlyoffice:80`
- Used by: 浏览器、ONLYOFFICE 静态资源加载流程

**API Controller Layer:**
- Purpose: 暴露 REST 接口，接住前端请求与 ONLYOFFICE 回调
- Contains: `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java`
- Depends on: service 层与 Spring MVC
- Used by: Vue 前端、ONLYOFFICE Docs

**Service Layer:**
- Purpose: 封装配置生成、JWT、文档存取、远程资源代理、保存状态管理
- Contains: `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/`
- Depends on: `DemoProperties`、`RestClient`、Java NIO、JJWT
- Used by: `DocumentController` 与 `OnlyofficeDemoApplication`

**Configuration / Model Layer:**
- Purpose: 承载配置映射、输入输出模型与跨层数据结构
- Contains: `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/` 与 `.../model/`
- Depends on: Spring Boot 配置绑定、Jakarta Validation
- Used by: controller 和 service 层

## Data Flow

**Editor Bootstrap Flow:**
1. 浏览器访问入口页面，由 `packages/web/src/App.vue` 在 `onMounted` 时请求 `GET /api/documents/{documentId}/editor-config`
2. `DocumentController.editorConfig()` 初始化保存状态，并调用 `OnlyofficeConfigService.buildEditorConfig(...)`
3. `OnlyofficeConfigService` 通过 `DocumentStorageService.getOrCreateDocument(...)` 获取或创建文档
4. 服务端拼出 `config.document`、`config.editorConfig`、`token` 与 `documentServerUrl`
5. 前端把响应直接传给 `DocumentEditor` 组件，开始加载 ONLYOFFICE 编辑器

**Save Callback Flow:**
1. 编辑器保存后，ONLYOFFICE 调用 `POST /api/documents/{documentId}/callback`
2. `DocumentController.callback()` 记录回调状态
3. 当 `status=2` 或 `status=6` 时，由 `DocumentStorageService.saveCallbackDocument(...)` 二次下载最新文件
4. 文档被覆盖写入本地存储，`DocumentStatusService` 更新内存状态
5. 前端每 5 秒轮询 `GET /api/documents/{documentId}/save-status` 展示最近保存结果

**Remote Import / Image Flow:**
1. 前端上传本地文件或提交远程 URL
2. `DocumentStorageService` 保存上传文件或下载远程文档到本地
3. 插图场景下，`OnlyofficeImageService` 生成签名 payload，并把图片真实地址隐藏在代理接口之后
4. ONLYOFFICE 再通过内部 URL 回源拉取文档或图片字节

**State Management:**
- 文档内容状态: 文件系统中的实际文档文件
- 运行态保存状态: `DocumentStatusService` 的内存 `ConcurrentHashMap`
- 前端页面状态: `App.vue` 中的 Vue `ref()` 状态

## Key Abstractions

**StoredDocument:**
- Purpose: 统一表达 `documentId`、标题、文件类型、文档类型、路径、最后修改时间
- Examples: `packages/server/src/main/java/com/earmo/onlyoffice/demo/model/StoredDocument.java`
- Pattern: record-style 数据载体，供多个 service 复用

**Service Classes:**
- Purpose: 把 ONLYOFFICE 协议、文档存储、图片代理、状态跟踪从控制器中拆出
- Examples: `OnlyofficeConfigService`、`DocumentStorageService`、`OnlyofficeImageService`、`DocumentStatusService`
- Pattern: Spring `@Service` 单例组件

**Configuration Root:**
- Purpose: 把散落环境变量收束到同一个对象模型
- Examples: `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/DemoProperties.java`
- Pattern: `@ConfigurationProperties(prefix = "demo")`

## Entry Points

**Backend Application:**
- Location: `packages/server/src/main/java/com/earmo/onlyoffice/demo/OnlyofficeDemoApplication.java`
- Triggers: `mvn spring-boot:run`、Docker 容器启动
- Responsibilities: 启动 Spring Boot，并预热 `demo` 文档

**Frontend Application:**
- Location: `packages/web/src/main.js`
- Triggers: Vite 开发环境或 nginx 提供的静态页面
- Responsibilities: 初始化 Vue 应用并挂载根组件

**HTTP Routes:**
- Location: `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java`
- Triggers: 浏览器 API 请求、ONLYOFFICE 下载请求、回调请求
- Responsibilities: 输入校验、委派服务、返回标准响应

## Error Handling

**Strategy:** service 层通过抛异常表达失败，controller 边界之外由统一异常处理器转换成 HTTP 响应

**Patterns:**
- 参数类问题通常抛 `IllegalArgumentException`
- 上传体积超限通过 `MaxUploadSizeExceededException` 单独处理
- 兜底错误由 `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/GlobalExceptionHandler.java` 返回通用 500 提示
- 前端以 `readErrorMessage()` 尝试读取后端 JSON 错误消息并落到界面态

## Cross-Cutting Concerns

**Validation:**
- 请求体使用 Jakarta Validation 注解
- URL、文件扩展名、是否为本地回环地址由 service 内部二次校验

**Authentication:**
- 没有最终用户鉴权
- ONLYOFFICE 集成依赖共享 JWT 密钥保证 config 与 insertImage payload 可被接受

**Networking:**
- 浏览器侧尽量走同源根路径 `/`
- 容器内部回调与下载统一走 `internalBaseUrl`

**CORS:**
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/WebConfig.java` 对 `/api/**` 全开放，便于本地开发联调

---

*Architecture analysis: 2026-03-17*
*Update when major patterns change*
