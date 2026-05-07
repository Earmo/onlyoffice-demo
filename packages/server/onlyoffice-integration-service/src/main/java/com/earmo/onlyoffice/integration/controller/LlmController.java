package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.common.exception.BaseException;
import com.earmo.onlyoffice.integration.model.ResponseDto;
import com.earmo.onlyoffice.integration.model.llm.request.*;
import com.earmo.onlyoffice.integration.model.llm.response.*;
import com.earmo.onlyoffice.integration.service.llm.LlmConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 暴露文档 AI 助手相关接口。
 *
 * <p>该控制器只负责 HTTP 协议适配和统一响应包装，具体会话、消息、请求状态和
 * 流式响应编排由 {@link LlmConversationService} 承担。
 */
@Tag(name = "AI 助手接口", description = "提供文档 AI 会话、消息流、请求状态和版本切换能力。")
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController extends BaseController {

    private final LlmConversationService llmConversationService;

    /**
     * 查询指定文档可用的 AI provider 和模型能力。
     *
     * @param request 能力查询请求体。
     * @return 当前文档可用的 AI 能力配置。
     */
    @PostMapping("/capability/query")
    @Operation(summary = "查询 AI 能力", description = "根据内部 documentId 返回当前服务可用的 provider、模型和默认选项。")
    public ResponseDto<LlmCapabilityResponse> capability(@Valid @RequestBody LlmCapabilityReq request) {
        return successResponseWithData(llmConversationService.getCapability(request.documentId()));
    }

    /**
     * 查询指定文档下的 AI 会话列表。
     *
     * @param request 会话列表查询请求体。
     * @return 会话摘要列表。
     */
    @PostMapping("/sessions/list")
    @Operation(summary = "查询 AI 会话列表", description = "返回指定文档下当前用户可访问的 AI 会话摘要列表。")
    public ResponseDto<List<LlmSessionSummaryResponse>> listSessions(@Valid @RequestBody LlmSessionListReq request) {
        return successResponseWithData(llmConversationService.listSessions(request.documentId()));
    }

    /**
     * 创建 AI 会话。
     *
     * @param request 创建会话请求体。
     * @return 新建会话详情。
     */
    @PostMapping("/sessions/create")
    @Operation(summary = "创建 AI 会话", description = "在指定文档下创建一个新的 AI 对话会话。")
    public ResponseDto<LlmSessionDetailResponse> createSession(@Valid @RequestBody CreateLlmSessionRequest request) {
        return successResponseWithData(llmConversationService.createSession(request));
    }

    /**
     * 查询单个 AI 会话详情。
     *
     * @param request 会话详情查询请求体。
     * @return 会话详情，包含消息和可展示版本。
     */
    @PostMapping("/sessions/detail")
    @Operation(summary = "查询 AI 会话详情", description = "根据 documentId 和 sessionId 返回会话详情。")
    public ResponseDto<LlmSessionDetailResponse> getSession(@Valid @RequestBody LlmSessionGetReq request) {
        return successResponseWithData(llmConversationService.getSession(request.documentId(), request.sessionId()));
    }

    /**
     * 删除指定 AI 会话。
     *
     * @param request 会话删除请求体。
     * @return 空成功响应。
     */
    @PostMapping("/sessions/delete")
    @Operation(summary = "删除 AI 会话", description = "删除指定文档下的 AI 会话及其消息。")
    public ResponseDto<Object> deleteSession(@Valid @RequestBody LlmSessionDeleteReq request) {
        llmConversationService.deleteSession(request.documentId(), request.sessionId());
        return successResponse();
    }

    /**
     * 重命名指定 AI 会话。
     *
     * @param request 会话重命名请求体。
     * @return 空成功响应。
     */
    @PostMapping("/sessions/rename")
    @Operation(summary = "重命名 AI 会话", description = "修改指定 AI 会话的展示标题。")
    public ResponseDto<Object> renameSession(@Valid @RequestBody LlmSessionRenameReq request) {
        llmConversationService.renameSession(request.documentId(), request.sessionId(), request.title());
        return successResponse();
    }

    /**
     * 以 SSE 方式发送 AI 消息。
     *
     * @param request 发送消息请求体。
     * @return SSE emitter。
     */
    @PostMapping(path = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式发送 AI 消息", description = "发送用户问题并通过 SSE 返回 assistant 生成过程和最终结果。")
    public Object streamMessage(@Valid @RequestBody SendLlmMessageRequest request) {
        try {
            return llmConversationService.streamMessage(request);
        } catch (IllegalArgumentException ex) {
            ResponseDto<Object> body = new ResponseDto<>();
            body.setCode("BAD_REQUEST");
            body.setMessage(ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (BaseException ex) {
            ResponseDto<Object> body = new ResponseDto<>();
            body.setCode(ex.getCode());
            body.setMessage(ex.getMessage());
            return ResponseEntity.status(ex.getHttpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }
    }

    /**
     * 切换 assistant 消息的当前展示版本。
     *
     * @param messageId assistant 消息 ID。
     * @param request   版本切换请求体。
     * @return 切换后的 assistant 消息。
     */
    @PutMapping("/messages/{messageId}/active-variant")
    @Operation(summary = "切换消息展示版本", description = "把指定 assistant 消息切换到目标 variant，用于重生成结果后的版本选择。")
    public ResponseDto<LlmMessageResponse> setActiveVariant(
            @Parameter(description = "assistant 消息 ID。", example = "message-2")
            @PathVariable String messageId,
            @Valid @RequestBody SetLlmActiveVariantRequest request
    ) {
        return successResponseWithData(llmConversationService.setActiveVariant(request.documentId(), messageId, request));
    }

    /**
     * 查询一次 AI 请求的状态。
     *
     * @param request 请求状态查询请求体。
     * @return AI 请求状态。
     */
    @PostMapping("/requests/detail")
    @Operation(summary = "查询 AI 请求状态", description = "根据 requestId 返回一次 AI 生成请求的当前状态和错误信息。")
    public ResponseDto<LlmRequestStatusResponse> getRequest(@Valid @RequestBody LlmRequestGetReq request) {
        return successResponseWithData(llmConversationService.getRequest(request.documentId(), request.requestId()));
    }

    /**
     * 取消一次正在执行的 AI 请求。
     *
     * @param request 请求取消请求体。
     * @return 取消后的请求状态。
     */
    @PostMapping("/requests/cancel")
    @Operation(summary = "取消 AI 请求", description = "取消指定文档下仍在执行的 AI 生成请求。")
    public ResponseDto<LlmRequestStatusResponse> cancelRequest(@Valid @RequestBody LlmRequestCancelReq request) {
        return successResponseWithData(llmConversationService.cancelRequest(request.documentId(), request.requestId()));
    }

}
