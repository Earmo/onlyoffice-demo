package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * 查询单次 AI 请求状态时返回的快照。
 */
@Schema(description = "查询单次 AI 请求状态时返回的快照。")
public record LlmRequestStatusResponse(
        @Schema(description = "请求所属内部文档 ID。", example = "sample")
        String documentId,
        @Schema(description = "AI 请求 ID。", example = "request-1")
        String requestId,
        @Schema(description = "AI 会话 ID。", example = "session-1")
        String sessionId,
        @Schema(description = "本次请求写入的 assistant message ID。", example = "message-2")
        String assistantMessageId,
        @Schema(description = "本次请求生成或更新的 variant ID。", example = "variant-1")
        String variantId,
        @Schema(description = "本次请求生成或更新的 variant 序号。", example = "0")
        Integer variantIndex,
        @Schema(description = "当前 assistant message 的 active variant 序号。", example = "0")
        Integer activeVariantIndex,
        @Schema(description = "请求状态。", example = "completed")
        String status,
        @Schema(description = "请求完成后可展示的 assistant 正文。")
        String assistantText,
        @Schema(description = "模型调用 token 用量。")
        LlmUsageResponse usage,
        @Schema(description = "模型返回的结束原因。", example = "stop")
        String finishReason,
        @Schema(description = "白名单过滤后的 provider 响应元数据。")
        Map<String, Object> providerResponseMeta,
        @Schema(description = "失败时的稳定错误码。", example = "LLM_PROVIDER_ERROR")
        String errorCode,
        @Schema(description = "请求开始时间。")
        Instant startedTime,
        @Schema(description = "请求结束时间。")
        Instant finishedTime
) {

    /**
     * 向后兼容旧调用点的构造器。
     *
     * <p>旧逻辑不感知 variant 字段，统一将 variantId、variantIndex 和 activeVariantIndex 置空。
     */
    public LlmRequestStatusResponse(
            String documentId,
            String requestId,
            String sessionId,
            String assistantMessageId,
            String status,
            String assistantText,
            LlmUsageResponse usage,
            String finishReason,
            Map<String, Object> providerResponseMeta,
            String errorCode,
            Instant startedTime,
            Instant finishedTime
    ) {
        this(
                documentId,
                requestId,
                sessionId,
                assistantMessageId,
                null,
                null,
                null,
                status,
                assistantText,
                usage,
                finishReason,
                providerResponseMeta,
                errorCode,
                startedTime,
                finishedTime
        );
    }
}
