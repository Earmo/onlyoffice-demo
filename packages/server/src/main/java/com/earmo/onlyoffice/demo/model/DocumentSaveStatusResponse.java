package com.earmo.onlyoffice.demo.model;

import java.time.Instant;

/**
 * 返回给前端的最近一次保存状态。
 *
 * @param documentId 当前文档 ID
 * @param state 当前状态：idle/callback-received/saved/save-failed
 * @param message 对当前状态的简短说明
 * @param lastCallbackStatus 最近一次 ONLYOFFICE callback 的 status
 * @param lastCallbackAt 最近一次收到 callback 的时间
 * @param lastSavedAt 最近一次成功落盘的时间
 */
public record DocumentSaveStatusResponse(
    String documentId,
    String state,
    String message,
    Integer lastCallbackStatus,
    Instant lastCallbackAt,
    Instant lastSavedAt
) {
}
