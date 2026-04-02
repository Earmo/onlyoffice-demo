package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 返回给前端的最近运行事件摘要。
 *
 * @param eventType 事件类型
 * @param message 事件说明
 * @param callbackStatus 关联的 ONLYOFFICE callback 状态码
 * @param eventTime 事件发生时间
 */
@Schema(description = "文档最近运行事件摘要。")
public record DocumentSaveStatusEventResponse(
    @Schema(description = "运行事件类型。", example = "save_succeeded")
    String eventType,
    @Schema(description = "给前端展示的事件说明。", example = "最新修改已成功回写到共享存储。")
    String message,
    @Schema(description = "关联的 ONLYOFFICE callback 状态码。", example = "2")
    Integer callbackStatus,
    @Schema(description = "运行事件发生时间。")
    Instant eventTime
) {
}
