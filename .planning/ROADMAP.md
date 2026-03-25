# Roadmap: OnlyOffice Document Service

## Overview

这轮 roadmap 把当前仓库从“最小 ONLYOFFICE 集成示例”重构为“可独立部署、也可嵌入其他系统的分布式文档编辑服务”。顺序上先建立服务边界和共享数据基础，再落存储策略与用户适配层，随后重做首页文档列表与编辑入口，最后锁定分布式编辑链路和交付验证能力。

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Service Foundation** - 建立独立服务与微服务接入所需的边界、配置和共享模型 (completed 2026-03-19)
- [x] **Phase 2: Storage Strategy Layer** - 抽象存储接口并落地 MinIO 作为首个可用实现 (completed 2026-03-23)
- [x] **Phase 3: User Context Integration** - 引入低耦合用户上下文接入能力 (completed 2026-03-23)
- [x] **Phase 4: Document Library Experience** - 把首页改为文档列表，并建立选择/上传后进入编辑器的流程 (completed 2026-03-25)
- [ ] **Phase 5: Distributed Editing Flow** - 让元数据、编辑配置、回调与安全边界适配分布式部署
- [ ] **Phase 6: Verification and Delivery** - 补齐自动化验证和接入文档，形成可交付服务基线
- [x] **Phase 7: 模块拆分、命名规范收敛与数据访问层重构** - 清理 demo 命名、拆分数据库模块，并把自定义查询与字段命名收敛到统一规范 (completed 2026-03-23)

## Phase Details

### Phase 1: Service Foundation
**Goal**: 明确这个项目作为独立服务和微服务的边界，建立可分布式部署所需的共享模型与基础配置结构。
**Depends on**: Nothing (first phase)
**Requirements**: [ARCH-01, ARCH-02, ARCH-03]
**Success Criteria** (what must be TRUE):
  1. 服务可以清晰配置为前后端分离部署，而不是默认绑定单一演示形态。
  2. 文档元数据与服务接口边界被定义清楚，便于其他系统接入。
  3. 核心状态不再被设计为只能依赖单机内存或本地目录存在。
**Plans**: 3 plans

Plans:
- [x] 01-01: 重新定义服务部署模型与外部地址配置
- [x] 01-02: 建立文档元数据模型与共享持久化基础
- [x] 01-03: 梳理对外接口和微服务接入边界

### Phase 2: Storage Strategy Layer
**Goal**: 抽象存储能力并以 MinIO 跑通首个可用策略，为 COS / OSS 预留一致扩展面。
**Depends on**: Phase 1
**Requirements**: [STOR-01, STOR-02, STOR-03]
**Success Criteria** (what must be TRUE):
  1. 文档读写、列举、上传和保存回写都通过统一存储接口工作。
  2. MinIO 可以驱动文档列表和编辑文件读写。
  3. 后续接入 COS / OSS 时不需要改动上层编辑流程。
**Plans**: 3 plans

Plans:
- [x] 02-01: 抽取统一存储策略接口与领域对象
- [x] 02-02: 实现 MinIO 文档存储与回写能力
- [x] 02-03: 固化多存储策略的配置和扩展约定

### Phase 3: User Context Integration
**Goal**: 引入真实用户上下文，并通过低耦合适配方式让外部系统用户身份进入文档服务。
**Depends on**: Phase 2
**Requirements**: [USER-01, USER-02, USER-03]
**Success Criteria** (what must be TRUE):
  1. 编辑配置不再写死演示用户，而是可解析真实用户上下文。
  2. 用户对接方式不会把具体认证方案耦合进文档核心逻辑。
  3. 文档列表、编辑和审计都能感知当前用户身份。
**Plans**: 3 plans

Plans:
- [x] 03-01: 设计用户上下文抽象与接入契约
- [x] 03-02: 把用户上下文接入编辑配置和文档操作
- [x] 03-03: 预留外部微服务用户体系对接方式

### Phase 4: Document Library Experience
**Goal**: 把首页改造成文档列表入口，让用户选择或上传文档后再进入编辑器。
**Depends on**: Phase 3
**Requirements**: [LIB-01, LIB-02, LIB-03]
**Success Criteria** (what must be TRUE):
  1. 首页默认显示文档列表，而不是自动打开固定文档。
  2. 用户可以从列表中选择文档进入编辑器。
  3. 用户可以从首页新建、上传或导入文档，结果会先回流列表并高亮，再显式进入编辑流程。
**Plans**: 3 plans

Plans:
- [x] 04-01: 建立文档列表 API 与前端列表页面
- [x] 04-02: 重构编辑入口，使选择文档后再加载编辑器
- [x] 04-03: 打通上传新文档并跳转编辑流程

### Phase 5: Distributed Editing Flow
**Goal**: 让文档元数据、ONLYOFFICE 编辑链路和安全边界适应分布式部署与共享状态。
**Depends on**: Phase 4
**Requirements**: [EDIT-01, EDIT-02, SAFE-01, SAFE-02, SAFE-03]
**Success Criteria** (what must be TRUE):
  1. 文档列表和文档文件依赖共享存储与元数据，而不是本地目录猜测。
  2. ONLYOFFICE 配置、文件下载和回调在分布式部署下仍然闭环可用。
  3. 回调安全、远程资源边界和保存状态在多实例场景下仍然可信。
**Plans**: 3 plans

Plans:
- [ ] 05-01: 把文档元数据与编辑链路切到共享存储模型
- [ ] 05-02: 重做 ONLYOFFICE 分布式配置、下载与 callback 闭环
- [ ] 05-03: 补齐安全边界和分布式保存状态能力

### Phase 6: Verification and Delivery
**Goal**: 用自动化测试、运行脚本和接入文档，把项目变成可交付的服务基线。
**Depends on**: Phase 5
**Requirements**: [QUAL-01, QUAL-02, QUAL-03]
**Success Criteria** (what must be TRUE):
  1. 后端关键服务和路由具备自动化测试覆盖。
  2. 前端关键列表与编辑入口流程具备自动化测试覆盖。
  3. 团队可以按文档把项目当独立服务部署，或作为微服务集成到外部系统。
**Plans**: 3 plans

Plans:
- [ ] 06-01: 为后端关键路由、存储策略和用户上下文补测试
- [ ] 06-02: 为前端列表与编辑流程补测试
- [ ] 06-03: 统一验证命令并编写独立部署 / 微服务接入说明

### Phase 7: 模块拆分、命名规范收敛与数据访问层重构

**Goal**: 把当前单体中的数据库访问、命名遗留和 demo 痕迹集中清理，为 starter 形态和后续多模块演进打下稳定边界。
**Requirements**: [ARCH-04, DATA-01, DATA-02, MOD-01]
**Depends on:** Phase 6
**Plans:** 3 plans

**Success Criteria** (what must be TRUE):
  1. 自定义查询不再通过 `@Select` 直接散落在 Mapper 上，而是由专门的 repository 层承载 SQL 组装与查询方法。
  2. 数据库字段命名从 `*_at` 等历史风格收敛到统一规范，用户相关字段使用 `*_user`，时间相关字段使用 `*_time`。
  3. 服务与数据库相关代码完成模块拆分，服务模块通过依赖数据库模块使用持久化能力。
  4. 项目命名从 `onlyoffice-demo` / `demo` 风格收敛到 `onlyoffice-integration-starter` 方向，不再继续扩散旧命名。

Plans:
- [x] 07-01: 创建后端多模块骨架与构建边界
- [x] 07-02: 重构数据访问层并统一字段命名
- [x] 07-03: 清理 demo 命名并接通 starter 服务模块

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Service Foundation | 3/3 | Complete    | 2026-03-19 |
| 2. Storage Strategy Layer | 3/3 | Complete    | 2026-03-23 |
| 3. User Context Integration | 3/3 | Complete    | 2026-03-23 |
| 4. Document Library Experience | 3/3 | Complete    | 2026-03-25 |
| 5. Distributed Editing Flow | 0/3 | Not started | - |
| 6. Verification and Delivery | 0/3 | Not started | - |
| 7. 模块拆分、命名规范收敛与数据访问层重构 | 3/3 | Complete    | 2026-03-23 |
