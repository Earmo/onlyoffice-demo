# OnlyOffice Document Service

## What This Is

这是一个基于 `Spring Boot + Vue + ONLYOFFICE Docs` 的前后端分离在线文档编辑服务，已经完成 v1.0.0 里程碑交付。当前产品既可以独立部署成完整软件服务，也可以作为其他分布式系统中的文档编辑微服务接入，提供文档列表、创建/上传/导入、预览/编辑、分布式保存回写、可插拔存储策略和低耦合用户上下文接入能力。

## Core Value

任意上层系统都应该能以低耦合方式接入一个可分布式部署的文档编辑服务，让用户先看到自己的文档列表，再安全地选择、上传、打开并保存文档。

## Current State

- 已发布 **v1.0.0**，范围覆盖 12 个 phase、36 个 plan。
- 根级 `npm run verify` 已通过：后端测试、前端测试、前端构建和 `docker compose config` 都可执行。
- 当前服务能力包括：文档列表与最近文档、创建/上传/远程导入、预览/编辑分流、ONLYOFFICE callback 保存闭环、编辑会话收敛、逻辑删除、后端分页、MinIO/COS provider、请求头/JWT 用户上下文策略。
- 当前源码规模约 24,564 行（排除 `node_modules`、`target`、`dist`），验证基线为 95 个后端测试和 18 个前端测试。

## Requirements

### Validated

- ✓ 浏览器可以通过同源入口打开 ONLYOFFICE 编辑器并编辑默认 `demo.docx` — existing
- ✓ 后端可以为指定文档生成 ONLYOFFICE `config + token` — existing
- ✓ ONLYOFFICE 可以从后端下载文档并把保存结果回调到 Spring Boot — existing
- ✓ 用户可以上传本地文档并切换到新文档继续编辑 — existing
- ✓ 用户可以导入远程文档 URL 并在编辑器中打开 — existing
- ✓ 用户可以在当前光标位置插入远程图片 — existing
- ✓ 页面可以展示最近一次保存回调与落盘状态 — existing
- ✓ 仓库已升级为前后端分离、可分布式部署的文档编辑服务 — v1.0.0
- ✓ 首页先展示文档列表，用户选择或上传文档后再进入编辑器 — v1.0.0
- ✓ 真实用户上下文已接入文档服务，并与外部用户体系保持低耦合 — v1.0.0
- ✓ 可插拔存储策略已落地 MinIO 和 COS，并保留统一扩展接口 — v1.0.0
- ✓ 服务既可单独部署，也可被其他系统作为文档编辑微服务集成 — v1.0.0

### Active

- [ ] 提供阿里云 OSS 存储策略实现
- [ ] 把 document-scoped authorization 显式建模为下一里程碑 requirement，而不是停留在租户级上下文注入
- [ ] 提供更复杂的文档权限控制模型（共享、只读、协作者）
- [ ] 支持历史版本、回滚和更完整的审计能力
- [ ] 提供更完整的外部系统事件通知或 webhook 能力

### Out of Scope

- 移动 App 客户端 — 当前仍聚焦 Web 服务和微服务接入模式
- 完整企业 IAM / SSO 产品化平台 — 当前只做低耦合用户接入能力，不做整套身份产品
- 复杂协同后台和版本中心 — 不属于 v1 已交付核心价值

## Context

- 当前仓库已经从最小 demo 收口成一个可运行的 OnlyOffice Document Service，具备独立部署文档、最小微服务集成文档和配置矩阵。
- 后端采用 `starter/data/service` 的模块边界，前端主要页面已统一到 Element Plus 组件体系。
- 存储策略已覆盖 local、MinIO、COS；用户上下文已覆盖 header、JWT 和自定义 provider SPI。
- 里程碑归档后，下一轮工作应从新的 REQUIREMENTS 与 ROADMAP 开始，而不是在 v1 执行历史上继续叠加范围漂移。

## Constraints

- **Tech stack**: 继续以 `Spring Boot + Vue + ONLYOFFICE` 作为基础，不推翻已验证的集成主链路。
- **Deployment**: 必须继续支持前后端分离和分布式部署，不能把关键状态重新绑回单机文件系统或单实例内存。
- **Integration**: 外部系统接入仍以低耦合上下文注入和稳定接口为优先，不在没有 requirement 的前提下隐式扩展复杂权限平台。
- **Storage**: 新 provider 继续复用统一策略抽象，不允许在上层编辑流程写死云厂商特性。
- **Quality**: 下一里程碑新增能力必须保持根级验证入口可执行，不接受再把测试基线放回“文档上可说、仓库里不跑”的状态。

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 把当前仓库作为 brownfield 基线继续演进 | 已有最小闭环和 codebase map，不应重新按空项目理解 | ✓ Good |
| 项目目标从“最小示例”升级为“分布式文档服务” | 用户给出了明确的新产品方向与集成诉求 | ✓ Good |
| 首页不再直接打开固定文档 | 文档列表 + 选择/上传后进入编辑器更符合真实服务形态 | ✓ Good |
| 用户体系采用低耦合适配方式接入 | 防止后续接入其他微服务用户体系时被当前实现绑死 | ✓ Good |
| 存储采用策略模式并以 MinIO 为首个实现 | 满足当前落地需求，同时给 COS / OSS 留出一致扩展面 | ✓ Good |
| 前端主界面统一收口到 Element Plus | 降低自定义样式维护成本并提升组件一致性 | ✓ Good |
| 根级 `npm run verify` 作为交付门槛 | 保证文档、测试与 compose 配置统一可执行 | ✓ Good |

## Next Milestone Goals

1. 定义 v1.x / v2 的新 REQUIREMENTS，明确 OSS、权限模型、历史版本与 webhook 的优先级。
2. 决定 document-scoped authorization 是继续保持租户级访问，还是升级为显式文档权限模型。
3. 在不破坏现有 verify 基线的前提下继续扩展存储与协作能力。

---
*Last updated: 2026-04-08 after shipping v1.0.0 milestone*
