# Requirements: OnlyOffice Demo

**Defined:** 2026-03-17
**Core Value:** 用户必须能通过一个统一入口稳定地打开、编辑并保存文档，而不需要手动处理多套地址、端口或编辑器配置细节。

## v1 Requirements

### Security

- [ ] **SEC-01**: 运维可以仅通过环境变量提供 ONLYOFFICE JWT 密钥与相关地址配置，而不依赖仓库中的演示默认值
- [ ] **SEC-02**: 服务端在处理 ONLYOFFICE 保存回调前会校验请求可信性，避免未授权请求触发文档覆盖
- [ ] **SEC-03**: 服务端在导入远程文档和代理远程图片时会阻止本地回环、私网滥用和异常响应大小，并返回明确错误

### Document Workflow

- [ ] **DOC-01**: 用户导入远程文档失败时能得到明确原因，而不会只看到模糊失败状态
- [ ] **DOC-02**: 用户能看到准确的最近保存状态，包括回调到达、落盘成功和落盘失败原因
- [ ] **DOC-03**: 用户在切换文档、切换只读模式和重新加载配置后，不会看到旧文档状态或错误残留

### Frontend Host

- [ ] **HOST-01**: 前端宿主页会被拆分为更易维护的模块，同时保留现有编辑、导入、插图和状态查看能力
- [ ] **HOST-02**: 控制台界面在桌面端和移动端都能稳定使用，并且关键操作语义清晰

### Quality

- [ ] **QUAL-01**: 后端 HTTP 路由层具备自动化测试，覆盖 editor-config、file、upload、import-remote、images、callback 和 save-status 关键路径
- [ ] **QUAL-02**: 前端关键流程具备自动化测试，至少覆盖加载配置、展示错误、切换模式和触发导入/插图动作
- [ ] **QUAL-03**: 仓库提供统一且可执行的本地验证入口，用于运行构建、测试和必要检查

### Repository Hygiene

- [ ] **REPO-01**: 前端包管理器策略明确，仓库不会同时维护多套互相竞争的锁文件
- [ ] **REPO-02**: 构建产物和测试报告不会继续作为源码输入长期保留在仓库中

## v2 Requirements

### Product Expansion

- **PROD-01**: 用户具备登录态和文档权限控制
- **PROD-02**: 文档元数据和保存状态持久化到数据库或其他共享存储
- **PROD-03**: 支持历史版本、多人协作隔离和更完整的审计能力

## Out of Scope

| Feature | Reason |
|---------|--------|
| 多租户权限平台 | 当前范围聚焦现有 demo 的集成硬化，而不是完整业务系统 |
| 移动 App 客户端 | 本轮只处理 Web 宿主页和后端集成 |
| 大规模对象存储/数据库重构 | 重要但不是初始化后第一批 phase 的最小必要条件 |
| 新增复杂编辑器业务功能 | 当前优先级是把现有链路做安全、稳定、可维护 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEC-01 | Unmapped | Pending |
| SEC-02 | Unmapped | Pending |
| SEC-03 | Unmapped | Pending |
| DOC-01 | Unmapped | Pending |
| DOC-02 | Unmapped | Pending |
| DOC-03 | Unmapped | Pending |
| HOST-01 | Unmapped | Pending |
| HOST-02 | Unmapped | Pending |
| QUAL-01 | Unmapped | Pending |
| QUAL-02 | Unmapped | Pending |
| QUAL-03 | Unmapped | Pending |
| REPO-01 | Unmapped | Pending |
| REPO-02 | Unmapped | Pending |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 0
- Unmapped: 13 ⚠️

---
*Requirements defined: 2026-03-17*
*Last updated: 2026-03-17 after initial definition*
