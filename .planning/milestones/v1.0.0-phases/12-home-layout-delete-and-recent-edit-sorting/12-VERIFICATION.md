# Phase 12: 首页布局收口、文档逻辑删除与最近编辑排序 - Verification Report

## Verification Criteria

1. 首页桌面端采用约 `1/5 : 4/5` 的双栏布局，左右主盒子能延伸到页面底边，CTA 与提示语层级符合新要求。
2. 文档支持逻辑删除；删除后默认列表和最近文档都不再展示该文档，标准预览 / 编辑 / 下载 / save-status 入口也不能再访问它。
3. 主列表与最近文档都以最近编辑时间倒序的后端结果为准，前端不再从当前页数据切片推导 recent。

## Automated Checks

- `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataRepositoryTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,AccessAuditServiceTest,DocumentStorageServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `cd packages/web && corepack pnpm test -- --run`
- `cd packages/web && corepack pnpm build`

## Result

- Passed: 后端逻辑删除、recent 查询、`lastEditedTime` 投影、删除冲突与 archived 访问守卫相关测试全部通过。
- Passed: 前端文档列表页、最近文档、删除刷新、高亮清理以及 `DocumentList` 组件交互回归全部通过。
- Passed: 前端生产构建通过，布局和交互改动没有破坏打包链路。

## Conclusion

Phase 12 的目标已经达到：首页布局和 CTA 层级完成收口，文档逻辑删除真正成为可用能力，列表与最近文档也都已经切到“最近编辑时间倒序”的后端真相源。
