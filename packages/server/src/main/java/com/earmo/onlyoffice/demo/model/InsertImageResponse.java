package com.earmo.onlyoffice.demo.model;

import java.util.Map;

/**
 * 返回给前端的 ONLYOFFICE insertImage 调用参数。
 *
 * @param insertImage 可直接传给 docEditor.insertImage(...) 的对象
 */
public record InsertImageResponse(Map<String, Object> insertImage) {
}

