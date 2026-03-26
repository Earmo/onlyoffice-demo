# 交付总览

这份文档面向“第一次接手仓库并准备交付”的开发者，帮助你快速判断应该走哪条路径。

## 你会用到的 3 类入口

- 独立部署
  适合把本项目直接作为一个完整文档服务运行
- 微服务接入
  适合把本项目作为上游业务系统中的文档编辑能力服务
- 统一验证
  适合在交付前快速确认后端、前端和 compose 基线都处于可发布状态

## 推荐执行顺序

1. 从仓库根执行 `npm run verify`
2. 按需要选择：
   - 独立部署：阅读 [独立部署说明](./standalone-deployment.md)
   - 微服务接入：阅读 [微服务接入说明](./microservice-integration.md)
3. 检查 [配置矩阵](./configuration-matrix.md)，确认环境变量与部署形态匹配
4. 交付前执行 [验收清单](./acceptance-checklist.md)

## 文档分工

- [minimal-integration.md](./minimal-integration.md)
  保持最小接入路径，不扩成完整运维说明
- [standalone-deployment.md](./standalone-deployment.md)
  说明独立服务部署方式
- [microservice-integration.md](./microservice-integration.md)
  说明上游系统如何以 API-first 方式接入
- [configuration-matrix.md](./configuration-matrix.md)
  汇总关键配置项和适用场景
- [acceptance-checklist.md](./acceptance-checklist.md)
  提供交付前的简洁人工验收清单
