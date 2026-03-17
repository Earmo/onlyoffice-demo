# OnlyOffice Demo

## What This Is

这是一个基于 `Spring Boot + Vue + ONLYOFFICE Docs` 的最小可运行集成仓库，用来演示浏览器如何通过同源入口加载 ONLYOFFICE 编辑器、获取签名配置、下载文档并接收保存回调。它主要面向需要验证 ONLYOFFICE 集成链路的开发者、方案验证场景和后续产品化改造工作的起点。

## Core Value

用户必须能通过一个统一入口稳定地打开、编辑并保存文档，而不需要手动处理多套地址、端口或编辑器配置细节。

## Requirements

### Validated

- ✓ 浏览器可以通过同源入口打开 ONLYOFFICE 编辑器并编辑默认 `demo.docx` — existing
- ✓ 后端可以为指定文档生成 ONLYOFFICE `config + token` — existing
- ✓ ONLYOFFICE 可以从后端下载文档并把保存结果回调到 Spring Boot — existing
- ✓ 用户可以上传本地文档并切换到新文档继续编辑 — existing
- ✓ 用户可以导入远程文档 URL 并在编辑器中打开 — existing
- ✓ 用户可以在当前光标位置插入远程图片 — existing
- ✓ 页面可以展示最近一次保存回调与落盘状态 — existing

### Active

- [ ] 让当前 demo 在安全性、配置管理和远程资源访问边界上更接近可上线的最小 MVP
- [ ] 把当前单文件前端宿主页拆到更易维护、可测试的结构
- [ ] 补齐后端 HTTP 层与前端关键编辑流程的自动化验证
- [ ] 清理仓库和运行链路中的演示性妥协，让本地开发与交付方式更一致

### Out of Scope

- 用户登录、权限模型和多租户文档隔离 — 当前仓库明确是最小集成示例，不做业务身份体系
- 历史版本、协同权限控制和复杂审计 — 超出当前“单文档编辑闭环”目标
- 独立移动端应用 — 当前聚焦 Web 宿主页与容器化集成
- 数据库存储和完整文档元数据平台化 — 本轮先保留本地文件系统方案，优先把现有链路做稳

## Context

- 当前代码已经具备前端宿主页、Spring Boot API、ONLYOFFICE Docs 容器与 nginx 同源代理的完整闭环。
- `.planning/codebase/` 已完成 codebase map，明确了现有技术栈、架构、约定、测试和风险点。
- 文档 `README.md` 与 `docs/minimal-integration.md` 都把本仓库定位为“最小可运行示例”，因此接下来的初始化目标不是发明新产品，而是为这个已有仓库建立可持续规划基线。
- 当前已知问题集中在四类：默认密钥与 callback 安全、远程资源边界、前端 `App.vue` 过于集中、自动化测试覆盖不足。

## Constraints

- **Tech stack**: 延续现有 `Spring Boot + Vue + ONLYOFFICE + nginx + Docker Compose` — 这是仓库已经验证可跑通的集成基础
- **Compatibility**: 保持同源入口与当前 `/api`、ONLYOFFICE 代理路径模型 — 避免破坏现有最关键演示路径
- **Scope**: 优先硬化和整理现有能力，不引入完整账号体系、数据库或大型新子系统 — 保持迭代聚焦
- **Security**: 不能再依赖示例默认密钥或未校验 callback 作为长期方案 — 当前风险已经在 codebase concerns 中明确
- **Quality**: 新的 phase 必须带可验证结果，而不是只继续堆叠 demo 功能 — 否则项目会继续停留在脆弱示例状态

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 把当前仓库按 brownfield 项目初始化 | 已有完整代码与 codebase map，不应按空项目处理 | ✓ Good |
| 将现有已跑通链路视为初始 Validated requirements | 这些能力已经在仓库中真实存在且可读可查 | ✓ Good |
| 当前规划聚焦“从 demo 到可维护 MVP” | 相比新增花哨功能，安全、结构和验证缺口更紧迫 | — Pending |
| 保留同源入口与 Docker Compose 集成形态 | 这是当前仓库最核心、最有价值的集成设计 | ✓ Good |

---
*Last updated: 2026-03-17 after initialization*
