package com.earmo.onlyoffice.integration.context.provider;

import com.earmo.onlyoffice.integration.context.AccessContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 访问上下文策略 SPI。
 *
 * <p>Phase 9 开始，这个接口被正式视为“访问上下文解析策略”：
 * 1. starter 内置 `header`、`jwt` 两种显式解析策略；
 * 2. `default` 作为默认补齐策略存在，但不参与显式身份命中判断；
 * 3. 外部系统仍可继续注册自定义策略，而不需要改动 controller 或文档业务服务。
 */
public interface AccessContextProvider {

    /**
     * provider 的稳定名称。
     */
    String name();

    /**
     * 当前策略是否属于显式上下文来源。
     *
     * <p>显式策略表示“请求中真的携带了某种身份来源”，例如 Header 或 JWT。
     * 默认补齐策略虽然也实现了同一个 SPI，但它只负责兜底补齐，不应该被当成显式来源。
     */
    default boolean isExplicitStrategy() {
        return true;
    }

    /**
     * 尝试从当前请求中解析访问上下文。
     *
     * <p>没有命中时返回 empty；格式不合法或解析失败时抛出访问上下文异常，
     * 交由统一异常出口转换成明确的 4xx 错误。
     */
    Optional<AccessContext> resolve(HttpServletRequest request);
}
