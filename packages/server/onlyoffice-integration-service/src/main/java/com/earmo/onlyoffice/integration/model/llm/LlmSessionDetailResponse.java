package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 打开单个 AI 会话时返回的完整会话详情。
 */
@Schema(description = "打开单个 AI 会话时返回的完整会话详情。")
public record LlmSessionDetailResponse(
        @Schema(description = "AI 会话 ID。", example = "session-1")
        String sessionId,
        @Schema(description = "会话所属内部文档 ID。", example = "sample")
        String documentId,
        @Schema(description = "会话标题。", example = "合同风险审阅")
        String title,
        @Schema(description = "最近一次提问使用的选区快照文本。")
        String lastSnapshotText,
        @Schema(description = "最近一次提问是否为空选区。", example = "false")
        boolean lastSnapshotIsEmpty,
        @Schema(description = "最近一次提问关联的标题 ID。", example = "heading-1")
        String lastHeadingId,
        @Schema(description = "最近一次提问关联的标题文本。", example = "付款条款")
        String lastHeadingText,
        @Schema(description = "最近一次用户发起对话的时间。")
        Instant lastConversationTime,
        @Schema(description = "会话创建时间。")
        Instant createdTime,
        @Schema(description = "会话最近更新时间。")
        Instant updatedTime,
        @Schema(description = "会话内按时间排序的消息列表。")
        List<LlmMessageResponse> messages
) {
}
