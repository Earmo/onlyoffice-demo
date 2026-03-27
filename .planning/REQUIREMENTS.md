# Requirements: OnlyOffice Document Service

**Defined:** 2026-03-17
**Core Value:** 任意上层系统都应该能以低耦合方式接入一个可分布式部署的文档编辑服务，让用户先看到自己的文档列表，再安全地选择、上传、打开并保存文档。

## v1 Requirements

### Service Architecture

- [x] **ARCH-01**: 服务可以以前后端分离方式独立部署，并通过配置声明 web、api、ONLYOFFICE 等外部地址
- [x] **ARCH-02**: 服务提供稳定的文档编辑接口与数据模型，便于被其他分布式系统作为文档微服务接入
- [x] **ARCH-03**: 服务在多实例部署场景下不依赖单机内存或本地文件路径作为核心共享状态
- [x] **ARCH-04**: 后端构建应支持按模块拆分 service 与 data 能力，并保持服务模块对数据库模块的单向依赖
- [x] **CFG-01**: 后端配置按 `dev / test / prod` 环境拆分，并通过统一入口配置选择当前 profile 与运行时参数

### Data Access and Schema Conventions

- [x] **DATA-01**: 自定义数据库查询不通过 `@Select` 等注解直接散落在 Mapper 上，而是由 repository 层统一承载查询方法与 SQL 组装
- [x] **DATA-02**: 数据库字段命名统一收敛为领域语义命名，其中用户相关字段使用 `*_user`，时间相关字段使用 `*_time`

### Module and Naming Hygiene

- [x] **MOD-01**: 后端模块、artifact、类名、目录名和配置命名不再继续扩散 `demo` 风格，统一向 `onlyoffice-integration-starter` 收敛
- [x] **MOD-02**: 后端 Maven 坐标和模块命名保持语义一致，避免 parent 与 service 模块的 artifactId 产生歧义

### Service Layer Conventions

- [x] **SVC-01**: `service` 包采用 `Service 接口 + ServiceImpl` 结构组织业务能力，便于替换实现与分层维护

### Storage Strategy

- [x] **STOR-01**: 系统具备统一的文档存储策略接口，文档读写、列举、上传和保存回写都通过该抽象完成
- [x] **STOR-02**: v1 提供 MinIO 存储策略实现，支持文档列表、文件读取、上传保存和 callback 回写
- [x] **STOR-03**: 存储扩展点足够稳定，使腾讯云 COS 和阿里云 OSS 后续可以作为新策略接入而不改动上层编辑流程
- [x] **STOR-04**: 提供腾讯云 COS 存储策略实现

### User Context

- [x] **USER-01**: 每次文档编辑会话都会使用真实用户上下文，而不是固定演示用户
- [x] **USER-02**: 用户接入层通过适配接口或上下文解析实现，能够对接外部系统已有的用户体系
- [x] **USER-03**: 文档列表、编辑配置和保存审计都能消费当前用户上下文，而不把认证实现耦合进文档核心逻辑
- [ ] **USER-04**: 访问上下文解析采用可扩展策略模式，内置请求头和 JWT 两类解析策略，并允许注册更多自定义策略

### Document Library and Editing

- [x] **LIB-01**: 用户打开首页时先看到文档列表，而不是自动进入固定文档编辑页
- [x] **LIB-02**: 用户可以从文档列表中选择一个已有文档，再进入编辑器进行查看或编辑
- [x] **LIB-03**: 用户可以在列表页新建、上传或导入文档，成功后结果会先回流列表并高亮，再显式进入该文档的编辑流程
- [ ] **LIB-04**: 文档列表同时提供预览和编辑两个入口；预览以只读模式查看文档，编辑入口单独进入可编辑工作台
- [x] **EDIT-01**: 文档元数据与文档列表信息会持久化到适合分布式部署的共享存储，而不是仅靠本地目录推导
- [x] **EDIT-02**: ONLYOFFICE editor config、文件下载和 callback 保存流程在前后端分离部署下仍然可用
- [ ] **EDIT-03**: 用户返回列表或切换文档时会显式结束当前编辑会话，并同步收敛列表中的“编辑中”状态

### Security and Reliability

- [x] **SAFE-01**: ONLYOFFICE 保存回调会做可信性校验，避免未授权请求触发文档覆盖
- [x] **SAFE-02**: 远程资源导入与代理会阻止本地回环、私网滥用和异常响应大小，并返回明确错误
- [x] **SAFE-03**: 编辑状态和保存结果对当前用户可见，失败原因可追踪，不因实例切换而丢失关键状态

### Quality and Delivery

- [x] **QUAL-01**: 后端关键路由与核心服务具备自动化测试，覆盖文档列表、编辑配置、上传、回调、存储策略和用户上下文
- [x] **QUAL-02**: 前端关键流程具备自动化测试，至少覆盖列表加载、选择文档、上传文档和进入编辑器
- [x] **QUAL-03**: 仓库提供统一且可执行的本地验证入口，并明确独立部署与微服务接入方式

### Code Documentation

- [x] **DOC-01**: 核心业务代码、配置和关键流程包含更详细的中文实现注释与步骤解释，便于团队维护和交接

### Editor Workbench Experience

- [ ] **UI-01**: 编辑页采用固定控制台与编辑器同层布局，支持顶部提示区收起，并保持顶部工具栏与右侧信息栏在可视区域内稳定可用

## v2 Requirements

### Storage Expansion

- **STOR-05**: 提供阿里云 OSS 存储策略实现

### Product Expansion

- **PROD-01**: 文档权限控制细化到共享、只读、协作者等更复杂模型
- **PROD-02**: 支持历史版本、回滚和更完整的审计能力
- **PROD-03**: 提供更完整的外部系统事件通知或 webhook 能力

## Out of Scope

| Feature | Reason |
|---------|--------|
| 移动 App 客户端 | 当前只聚焦 Web 服务和微服务接入 |
| 完整企业 IAM / SSO 平台 | 当前只做低耦合接入点，不做整套身份产品 |
| COS / OSS 的正式实现 | 先落地 MinIO 和存储策略抽象，再扩展云厂商实现 |
| 复杂协同后台和版本中心 | 不是当前服务化改造的第一阶段核心价值 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ARCH-01 | Phase 1 | Complete |
| ARCH-02 | Phase 1 | Complete |
| ARCH-03 | Phase 1 | Complete |
| STOR-01 | Phase 2 | Complete |
| STOR-02 | Phase 2 | Complete |
| STOR-03 | Phase 2 | Complete |
| USER-01 | Phase 3 | Complete |
| USER-02 | Phase 3 | Complete |
| USER-03 | Phase 3 | Complete |
| USER-04 | Phase 9 | Planned |
| LIB-01 | Phase 4 | Complete |
| LIB-02 | Phase 4 | Complete |
| LIB-03 | Phase 4 | Complete |
| LIB-04 | Phase 9 | Planned |
| EDIT-01 | Phase 5 | Complete |
| EDIT-02 | Phase 5 | Complete |
| EDIT-03 | Phase 9 | Planned |
| SAFE-01 | Phase 5 | Complete |
| SAFE-02 | Phase 5 | Complete |
| SAFE-03 | Phase 5 | Complete |
| QUAL-01 | Phase 6 | Complete |
| QUAL-02 | Phase 6 | Complete |
| QUAL-03 | Phase 6 | Complete |
| ARCH-04 | Phase 7 | Complete |
| DATA-01 | Phase 7 | Complete |
| DATA-02 | Phase 7 | Complete |
| MOD-01 | Phase 7 | Complete |
| CFG-01 | Phase 8 | Complete |
| MOD-02 | Phase 8 | Complete |
| SVC-01 | Phase 8 | Complete |
| STOR-04 | Phase 8 | Complete |
| DOC-01 | Phase 8 | Complete |
| UI-01 | Phase 9 | Planned |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-17*
*Last updated: 2026-03-27 after adding Phase 9*
