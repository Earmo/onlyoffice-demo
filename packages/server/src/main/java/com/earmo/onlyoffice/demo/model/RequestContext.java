package com.earmo.onlyoffice.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档服务消费的标准化请求上下文。
 */
@Schema(description = "文档服务消费的标准化请求上下文。")
public record RequestContext(
    @Schema(description = "租户 ID。", example = "native")
    String tenantId,
    @Schema(description = "来源系统标识。", example = "native")
    String sourceSystem,
    @Schema(description = "外部用户 ID。", example = "demo-user")
    String externalUserId,
    @Schema(description = "用户展示名。", example = "演示用户")
    String displayName
) {

  public String ownerUserId() {
    return externalUserId;
  }
}
