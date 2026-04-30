package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档服务消费的标准化请求上下文。
 *
 * <p>这里仍然保留“外部用户”这一语义，因为上游系统透传过来的身份信息未必是本服务自产生的用户主键。
 * 但在本服务内部落到文档归属字段时，统一映射成 `ownerUser`，避免 `*_user_id` 风格继续扩散。
 */
@Schema(description = "文档服务消费的标准化请求上下文。")
public record RequestContext(
        @Schema(description = "租户 ID。", example = "native")
        String tenantId,
        @Schema(description = "来源系统标识。", example = "native")
        String sourceSystem,
        @Schema(description = "外部用户标识。", example = "starter-user")
        String externalUser,
        @Schema(description = "用户展示名。", example = "默认用户")
        String displayName
) {

    public String ownerUser() {
        return externalUser;
    }
}
