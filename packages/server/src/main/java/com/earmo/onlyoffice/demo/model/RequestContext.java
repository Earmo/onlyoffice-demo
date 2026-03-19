package com.earmo.onlyoffice.demo.model;

/**
 * 文档服务消费的标准化请求上下文。
 */
public record RequestContext(
    String tenantId,
    String sourceSystem,
    String externalUserId,
    String displayName
) {

  public String ownerUserId() {
    return externalUserId;
  }
}
