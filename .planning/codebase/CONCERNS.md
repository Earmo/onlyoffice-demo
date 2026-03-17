# Codebase Concerns

**Analysis Date:** 2026-03-17

## Tech Debt

**Single-file frontend console in `packages/web/src/App.vue`:**
- Issue: 页面状态、编辑器集成、上传、远程导入、插图、轮询和面板 UI 都集中在一个组件中
- Why: 仓库定位是“最小可运行集成示例”
- Impact: 继续加功能时可读性、复用性和测试性会快速下降
- Fix approach: 拆为 `components/` + composables，例如 editor bootstrap、document import、save-status polling 分层

**No persistent metadata store in `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStorageService.java`:**
- Issue: `documentId` 直接映射文件系统，状态与元数据没有数据库支撑
- Why: 追求最小依赖与最少基础设施
- Impact: 无法做权限、历史版本、多租户、审计和稳定的文档索引
- Fix approach: 引入数据库表保存文档元数据，文件内容迁移到对象存储或独立文件服务

## Known Bugs / Behavioral Risks

**Callback status visibility resets on restart:**
- Symptoms: 服务重启后前端“最近保存状态”丢失，恢复为 `idle`
- Trigger: 重启 Spring Boot 进程
- Workaround: 无，只能依赖重启后的新回调重新填充状态
- Root cause: `DocumentStatusService` 使用内存 `ConcurrentHashMap`

**Potential duplicate document filename collisions during fast uploads:**
- Symptoms: 极短时间内重复上传同名文件可能碰撞到基于毫秒时间戳的 `documentId`
- Trigger: 高频并发上传同一文件名
- Workaround: 当前无显式重试或去重逻辑
- Root cause: `buildGeneratedDocumentId()` 仅使用清洗后的文件名加 `System.currentTimeMillis()`

## Security Considerations

**Shared JWT secret with demo defaults:**
- Risk: `packages/server/src/main/resources/application.yml` 与 `docker-compose.yml` 都存在默认密钥路径，示例配置若原样外泄会降低安全性
- Current mitigation: 文档中已有“生产前替换”的语义，但代码层没有强制
- Recommendations: 生产环境要求外部注入强随机密钥，启动时拒绝默认值

**ONLYOFFICE callback lacks signature verification:**
- Risk: 任意可访问 callback 地址的请求理论上可伪造回写流程
- Current mitigation: 当前仅依赖网络可达性和路径难猜，几乎不足以当作安全措施
- Recommendations: 为 `POST /api/documents/{documentId}/callback` 增加签名验签、来源校验和审计日志

**Overly broad CORS in `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/WebConfig.java`:**
- Risk: `allowedOriginPatterns("*")`、`allowedMethods("*")`、`allowedHeaders("*")` 对生产环境过宽
- Current mitigation: 仅适合本地开发便利性
- Recommendations: 改为基于环境配置的明确白名单

**Remote fetch SSRF surface in document/image import:**
- Risk: 虽然已禁止 localhost 回环地址，但仍可能访问内网或受限网段中的其他地址
- Current mitigation: 限制 scheme 与 host，拒绝本地回环
- Recommendations: 增加私网网段过滤、DNS 解析校验、大小限制与超时配置

## Performance Bottlenecks

**Save status polling every 5 seconds from `packages/web/src/App.vue`:**
- Problem: 页面存活期间固定轮询 `GET /api/documents/{documentId}/save-status`
- Measurement: 代码里写死 5000ms，没有节流策略
- Cause: 为了最小实现，采用简单轮询而不是事件或长连接
- Improvement path: 在编辑器事件、回调通知或可见性变化上做更智能的刷新

**Whole-file download and overwrite in callback path:**
- Problem: `saveCallbackDocument()` 每次保存都完整下载文件并全量覆盖
- Measurement: 未见实际数据，但大文件或高频保存会线性放大 I/O 与网络成本
- Cause: 示例架构没有差量同步或异步队列
- Improvement path: 增加文件大小控制、异步落盘、对象存储直传或版本化写入

## Fragile Areas

**nginx and ONLYOFFICE path proxying in `packages/web/nginx.conf`:**
- Why fragile: ONLYOFFICE 依赖多个路径前缀和 websocket/长连接代理，漏配一个就可能白屏或资源加载失败
- Common failures: `/web-apps/`、`/sdkjs/`、`/coauthoring/` 任一路径代理错误导致编辑器初始化失败
- Safe modification: 改前先在 Docker 环境验证完整编辑、保存、回调流程
- Test coverage: 没有自动化覆盖

**Editor config assembly in `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeConfigService.java`:**
- Why fragile: ONLYOFFICE 对字段结构和 URL 可达性比较敏感
- Common failures: JWT 不匹配、`internalBaseUrl` 不可达、`document.key` 规则变化导致刷新异常
- Safe modification: 修改前后至少保留现有 `OnlyofficeConfigServiceTest`，并手工验证实际编辑器加载
- Test coverage: 有基础单元测试，但缺少真实 ONLYOFFICE 集成验证

## Scaling Limits

**Storage model:**
- Current capacity: 受单机文件系统与单实例 JVM 限制
- Limit: 多实例部署时文档与保存状态无法天然共享
- Symptoms at limit: 某实例生成的状态另一实例看不到，文件读写不一致
- Scaling path: 共享对象存储 + 数据库 + 集中式状态或事件系统

**Runtime status model:**
- Current capacity: 适合单机演示
- Limit: `DocumentStatusService` 不支持多节点或持久化
- Symptoms at limit: 负载均衡后状态查询与实际保存节点脱节
- Scaling path: 持久化状态表或缓存层

## Dependencies at Risk

**Dual front-end lockfiles in `packages/web/`:**
- Risk: 同时存在 `package-lock.json` 与 `pnpm-lock.yaml`，容易让团队在包管理器和解析结果上分叉
- Impact: 依赖树、安装结果和复现性可能不一致
- Migration plan: 明确选定 npm 或 pnpm，并只保留一种锁文件

**Committed build outputs in `packages/server/target/`:**
- Risk: 构建产物与测试报告已进入仓库，容易造成噪音、误差和无关 diff
- Impact: 审查成本升高，也可能掩盖真实源码变更
- Migration plan: 清理已提交产物并通过 `.gitignore` 排除

## Missing Critical Features

**No user auth and authorization:**
- Problem: 当前所有文档操作默认对调用者开放
- Current workaround: 作为 demo 使用，不面向真实多用户环境
- Blocks: 无法支持受控文档访问、审计和多租户
- Implementation complexity: Medium

**No callback authenticity or remote resource governance:**
- Problem: 外部回调与远程资源访问还没有达到生产安全级别
- Current workaround: 依赖最小校验与演示环境
- Blocks: 无法安全上线公网环境
- Implementation complexity: Medium

## Test Coverage Gaps

**Controller/API layer:**
- What's not tested: `DocumentController` 的上传、远程导入、callback、文件下载与错误响应
- Risk: HTTP 层参数绑定或响应头变更可能无声回归
- Priority: High
- Difficulty to test: Low to Medium，可用 `MockMvc` 补齐

**Frontend editor host behavior:**
- What's not tested: `packages/web/src/App.vue` 的加载态、报错态、只读切换、轮询与导入流程
- Risk: 交互改动容易破坏真实用户路径
- Priority: High
- Difficulty to test: Medium，可引入 Vitest + Vue Test Utils 或 Playwright

---

*Concerns audit: 2026-03-17*
*Update as issues are fixed or new ones discovered*
