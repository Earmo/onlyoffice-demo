package com.earmo.onlyoffice.integration.model.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * AI SSE 流中的标准事件 payload。
 */
@Schema(description = "AI SSE 流中的标准事件 payload。")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmStreamEventResponse(
        @Schema(description = "事件所属内部文档 ID。", example = "sample")
        String documentId,
        @Schema(description = "AI 请求 ID。", example = "request-1")
        String requestId,
        @Schema(description = "AI 会话 ID。", example = "session-1")
        String sessionId,
        @Schema(description = "当前会话标题；request-started 事件中会返回。", example = "合同风险审阅")
        String sessionTitle,
        @Schema(description = "本次请求写入的 assistant message ID。", example = "message-2")
        String assistantMessageId,
        @Schema(description = "本次请求生成或更新的 variant ID。", example = "variant-1")
        String variantId,
        @Schema(description = "本次请求生成或更新的 variant 序号。", example = "0")
        Integer variantIndex,
        @Schema(description = "当前 assistant message 的 active variant 序号。", example = "0")
        Integer activeVariantIndex,
        @Schema(description = "实际使用的逻辑 provider。", example = "dashscope")
        String provider,
        @Schema(description = "实际使用的模型。", example = "qwen-plus")
        String model,
        @Schema(description = "assistant-delta 事件中的正文增量。")
        String delta,
        @Schema(description = "reasoning-delta 事件中的推理文本增量。")
        String reasoningText,
        @Schema(description = "终态事件中的完整 assistant 正文。")
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
     * 向后兼容旧流式事件构造器。
     *
     * <p>未显式传入 variant 字段的旧调用点，会把 variantId、variantIndex 和 activeVariantIndex 置空。
     */
    public LlmStreamEventResponse(
            String documentId,
            String requestId,
            String sessionId,
            String sessionTitle,
            String assistantMessageId,
            String provider,
            String model,
            String delta,
            String reasoningText,
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
                sessionTitle,
                assistantMessageId,
                null,
                null,
                null,
                provider,
                model,
                delta,
                reasoningText,
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
