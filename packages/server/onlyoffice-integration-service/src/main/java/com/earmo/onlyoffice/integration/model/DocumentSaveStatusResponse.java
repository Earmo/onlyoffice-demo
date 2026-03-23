package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 返回给前端的最近一次保存状态。
 *
 * @param documentId 当前文档 ID
 * @param state 当前状态：idle/callback-received/saved/save-failed
 * @param message 对当前状态的简短说明
 * @param lastCallbackStatus 最近一次 ONLYOFFICE callback 的 status
 * @param lastCallbackTime 最近一次收到 callback 的时间
 * @param lastSavedTime 最近一次成功落盘的时间
 */
@Schema(description = "文档最近一次保存状态响应。")
public record DocumentSaveStatusResponse(
    @Schema(description = "当前文档 ID。", example = "sample")
    String documentId,
    @Schema(description = "当前主状态。", example = "saved")
    String state,
    @Schema(description = "给前端展示的状态说明。", example = "最新修改已成功回写到共享存储。")
    String message,
    @Schema(description = "最近一次 ONLYOFFICE callback 状态码。", example = "2")
    Integer lastCallbackStatus,
    @Schema(description = "最近一次收到 callback 的时间。")
    Instant lastCallbackTime,
    @Schema(description = "最近一次成功保存回写的时间。")
    Instant lastSavedTime
) {
}
