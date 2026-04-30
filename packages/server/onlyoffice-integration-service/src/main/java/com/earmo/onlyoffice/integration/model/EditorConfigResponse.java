package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 返回给前端的编辑器初始化结果。
 *
 * @param documentServerUrl ONLYOFFICE Docs 对浏览器暴露的地址
 * @param config            ONLYOFFICE Vue 组件原样可消费的配置对象
 */
@Schema(description = "ONLYOFFICE 编辑器初始化响应。")
public record EditorConfigResponse(
        @Schema(description = "浏览器访问 ONLYOFFICE Docs 的地址。", example = "http://localhost:12333")
        String documentServerUrl,
        @Schema(description = "可直接交给 ONLYOFFICE 前端组件消费的 editor config。")
        Map<String, Object> config
) {
}


