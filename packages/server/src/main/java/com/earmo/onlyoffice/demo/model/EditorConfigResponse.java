package com.earmo.onlyoffice.demo.model;

import java.util.Map;

/**
 * 返回给前端的编辑器初始化结果。
 *
 * @param documentServerUrl ONLYOFFICE Docs 对浏览器暴露的地址
 * @param config ONLYOFFICE Vue 组件原样可消费的配置对象
 */
public record EditorConfigResponse(String documentServerUrl, Map<String, Object> config) {
}
