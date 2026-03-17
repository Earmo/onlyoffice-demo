# Roadmap: OnlyOffice Demo

## Overview

这轮 roadmap 把当前仓库从“已可运行的 ONLYOFFICE 集成示例”推进到“更安全、更稳、更易维护、可验证的 MVP 基线”。顺序上先收敛配置和仓库卫生，再补安全边界与文档流程稳定性，之后处理前端结构，最后用自动化验证把关键路径锁住。

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [ ] **Phase 1: Secure Configuration Baseline** - 清理演示默认值依赖并收敛仓库基础卫生
- [ ] **Phase 2: Trusted Integration Boundaries** - 为 callback 和远程资源访问补上可信边界
- [ ] **Phase 3: Stable Document Workflow** - 让导入、保存状态和模式切换在用户视角下更可靠
- [ ] **Phase 4: Frontend Host Refactor** - 把前端宿主页拆成更可维护、可扩展的结构
- [ ] **Phase 5: Verification and Delivery Readiness** - 用自动化测试和统一验证入口锁定行为

## Phase Details

### Phase 1: Secure Configuration Baseline
**Goal**: 清理仓库中依赖演示默认值的配置路径，并把基础仓库卫生问题先收住。
**Depends on**: Nothing (first phase)
**Requirements**: [SEC-01, REPO-01, REPO-02]
**Success Criteria** (what must be TRUE):
  1. 运维可以通过环境变量完成 ONLYOFFICE 关键配置，而无需依赖提交在仓库中的默认密钥。
  2. 前端包管理策略明确，团队不会再面对多套锁文件并存的歧义。
  3. 构建产物和测试报告不再作为源码长期跟踪，仓库输入更干净。
**Plans**: 3 plans

Plans:
- [ ] 01-01: 盘点并调整服务端与 Docker 配置入口，收敛示例默认值使用方式
- [ ] 01-02: 统一前端包管理器与锁文件策略，更新仓库说明
- [ ] 01-03: 清理并忽略构建产物目录，确保仓库只跟踪源码和必要文档

### Phase 2: Trusted Integration Boundaries
**Goal**: 补齐 ONLYOFFICE callback 与远程资源导入的安全边界，避免危险请求直接进入核心流程。
**Depends on**: Phase 1
**Requirements**: [SEC-02, SEC-03]
**Success Criteria** (what must be TRUE):
  1. 只有可信 callback 才会触发本地文档覆盖。
  2. 远程文档和图片导入会拒绝本地回环、私网滥用或异常响应。
  3. 用户在失败时能看到明确可操作的错误反馈，而不是无上下文失败。
**Plans**: 2 plans

Plans:
- [ ] 02-01: 为 callback 增加可信性校验与失败路径处理
- [ ] 02-02: 为远程文档和图片访问增加边界校验、超时和响应保护

### Phase 3: Stable Document Workflow
**Goal**: 提升文档导入、保存状态展示和模式切换的稳定性，减少用户看到的陈旧状态与歧义。
**Depends on**: Phase 2
**Requirements**: [DOC-01, DOC-02, DOC-03]
**Success Criteria** (what must be TRUE):
  1. 用户导入远程文档失败时能知道失败原因。
  2. 保存状态能准确反映 callback、落盘成功和失败信息。
  3. 切换文档、只读模式或重载配置后，不会残留旧错误或旧状态。
**Plans**: 3 plans

Plans:
- [ ] 03-01: 梳理并统一导入失败与错误消息反馈
- [ ] 03-02: 规范保存状态模型和前端轮询呈现
- [ ] 03-03: 收紧文档切换与模式切换时的状态重置逻辑

### Phase 4: Frontend Host Refactor
**Goal**: 拆解当前前端单文件宿主页，保留现有能力同时提升可维护性和响应式表现。
**Depends on**: Phase 3
**Requirements**: [HOST-01, HOST-02]
**Success Criteria** (what must be TRUE):
  1. 前端宿主页不再把全部逻辑堆在一个组件中。
  2. 控制台在桌面和移动端都可稳定使用。
  3. 编辑、导入、插图和状态查看能力在重构后保持可用。
**Plans**: 2 plans

Plans:
- [ ] 04-01: 拆分前端模块与共享状态边界
- [ ] 04-02: 优化控制台布局、文案与交互稳定性

### Phase 5: Verification and Delivery Readiness
**Goal**: 用自动化测试与统一验证入口锁住后端 HTTP 路由和前端关键流程。
**Depends on**: Phase 4
**Requirements**: [QUAL-01, QUAL-02, QUAL-03]
**Success Criteria** (what must be TRUE):
  1. 后端关键 HTTP 路由具备自动化测试覆盖。
  2. 前端核心宿主流程具备自动化测试覆盖。
  3. 仓库有统一的本地验证入口，开发者能按文档运行构建与测试。
**Plans**: 3 plans

Plans:
- [ ] 05-01: 为后端控制器与集成边界补测试
- [ ] 05-02: 为前端关键宿主流程补测试
- [ ] 05-03: 统一并文档化本地验证命令

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Secure Configuration Baseline | 0/3 | Not started | - |
| 2. Trusted Integration Boundaries | 0/2 | Not started | - |
| 3. Stable Document Workflow | 0/3 | Not started | - |
| 4. Frontend Host Refactor | 0/2 | Not started | - |
| 5. Verification and Delivery Readiness | 0/3 | Not started | - |
