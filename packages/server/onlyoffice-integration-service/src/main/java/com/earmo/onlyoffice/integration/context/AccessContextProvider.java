package com.earmo.onlyoffice.integration.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 访问上下文 provider SPI。
 *
 * <p>starter 自带 header、jwt、default 三种实现，但这里保留为 SPI，
 * 是为了让外部系统后续可以把自家用户中心、网关签名或会话透传逻辑接进来，
 * 而不需要改动 controller 或文档业务服务。
 */
public interface AccessContextProvider {

  /**
   * provider 的稳定名称。
   */
  String name();

  /**
   * 尝试从当前请求中解析访问上下文。
   *
   * <p>没有命中时返回 empty；格式不合法或解析失败时抛出访问上下文异常，
   * 交由统一异常出口转换成明确的 4xx 错误。
   */
  Optional<AccessContext> resolve(HttpServletRequest request);
}
