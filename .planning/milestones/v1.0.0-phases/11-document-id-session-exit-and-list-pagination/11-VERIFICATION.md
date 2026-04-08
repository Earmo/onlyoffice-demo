# Phase 11: 文档标识规则、编辑退出状态与列表分页后端化 - Verification Report

## Verification Criteria

1. 新建、上传和导入链路统一由服务端生成内部 `documentId`，不再复用标题或调用方传入 ID。
2. 文档列表接口返回分页元数据，分页与筛选全部由后端接口驱动。
3. 返回列表、切换文档和组件卸载不会重复发送 close-session，请求完成后列表状态能收敛到非 `editing` 的真实摘要。

## Automated Checks

- `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentStatusServiceTest,DocumentControllerTest,AccessContextErrorHandlingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `cd packages/web && corepack pnpm test -- --run`
- `cd packages/web && corepack pnpm build`

## Result

- Passed: 后端 ULID、分页列表契约、会话关闭状态投影相关定向测试全部通过。
- Passed: 前端文档列表页、编辑页、预览页、`EditorShell` 全量 Vitest 用例全部通过。
- Passed: 前端生产构建通过，Element Plus 在测试与生产环境都可稳定工作。

## Conclusion

Phase 11 的目标已经达到：内部文档标识规则完成统一，文档列表已切换为后端分页真相源，编辑页退出路径也已收口成可回归验证的一次性关闭流程。
