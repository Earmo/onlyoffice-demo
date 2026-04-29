package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContextException;
import com.earmo.onlyoffice.integration.model.ApiErrorResponse;
import com.earmo.onlyoffice.integration.service.DocumentNotFoundException;
import com.earmo.onlyoffice.integration.service.DocumentOperationConflictException;
import com.earmo.onlyoffice.integration.service.llm.LlmApiException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.DisconnectedClientHelper;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 统一处理接口异常，避免把框架异常直接暴露给前端。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String MAX_UPLOAD_MESSAGE = "上传文件超过大小限制，当前最大允许 50MB。";
  private static final String WINDOWS_CONNECTION_ABORTED_ZH = "中止了一个已建立的连接";
  private static final String WINDOWS_CONNECTION_RESET_ZH = "强迫关闭了一个现有的连接";

  /**
   * 处理上传文件超过限制的异常。
   *
   * @param exception 上传大小超限异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, MAX_UPLOAD_MESSAGE, null, response);
  }

  /**
   * 处理请求参数不合法异常。
   *
   * @param exception 参数异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), null, response);
  }

  /**
   * 处理文档不存在异常。
   *
   * @param exception 文档不存在异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<?> handleDocumentNotFound(DocumentNotFoundException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), null, response);
  }

  /**
   * 处理文档操作冲突异常。
   *
   * @param exception 文档操作冲突异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(DocumentOperationConflictException.class)
  public ResponseEntity<?> handleDocumentOperationConflict(DocumentOperationConflictException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.CONFLICT, exception.getMessage(), null, response);
  }

  /**
   * 处理访问上下文解析异常。
   *
   * @param exception 访问上下文异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(AccessContextException.class)
  public ResponseEntity<?> handleAccessContextException(AccessContextException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), null, response);
  }

  /**
   * 处理内部状态异常。
   *
   * @param exception 内部状态异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> handleIllegalState(IllegalStateException exception, HttpServletResponse response) {
    log.error("Internal state error", exception);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务端处理失败，请稍后重试。", null, response);
  }

  /**
   * 处理 AI 领域异常。
   *
   * @param exception AI API 异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(LlmApiException.class)
  public ResponseEntity<?> handleLlmApiException(LlmApiException exception, HttpServletResponse response) {
    return errorResponse(exception.httpStatus(), exception.getMessage(), exception.errorCode(), response);
  }

  /**
   * 处理异步请求超时异常。
   *
   * @param exception 异步请求超时异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(AsyncRequestTimeoutException.class)
  public ResponseEntity<?> handleAsyncRequestTimeout(AsyncRequestTimeoutException exception, HttpServletResponse response) {
    log.warn("Async request timed out", exception);
    return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "流式响应超时，请稍后重试。", null, response);
  }

  /**
   * 处理响应写入阶段的 I/O 异常。
   *
   * @param exception I/O 异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应或空响应。
   */
  @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
  public ResponseEntity<?> handleIoException(IOException exception, HttpServletResponse response) {
    if (isClientDisconnected(exception)) {
      log.warn("Client disconnected during response streaming: {}", exception.getMessage());
      return errorResponse(HttpStatus.NO_CONTENT, null, null, response);
    }
    log.error("I/O exception while writing response", exception);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务端处理失败，请稍后重试。", null, response);
  }

  /**
   * 处理未被更具体规则捕获的异常。
   *
   * @param exception 未处理异常。
   * @param response 当前 HTTP 响应。
   * @return 统一错误响应。
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGenericException(Exception exception, HttpServletResponse response) {
    log.error("Unhandled server exception", exception);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务端处理失败，请稍后重试。", null, response);
  }

  /**
   * 构造统一错误响应。
   *
   * @param status HTTP 状态码。
   * @param message 错误文案。
   * @param errorCode 稳定错误码，可为空。
   * @param response 当前 HTTP 响应。
   * @return 响应实体。
   */
  private ResponseEntity<?> errorResponse(HttpStatus status, String message, String errorCode, HttpServletResponse response) {
    if (response != null && response.isCommitted()) {
      return ResponseEntity.status(status).build();
    }
    if (message == null || message.isBlank()) {
      return ResponseEntity.status(status).build();
    }
    ApiErrorResponse body = errorCode == null ? new ApiErrorResponse(message) : new ApiErrorResponse(message, errorCode);
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  /**
   * 判断 I/O 异常是否来自客户端主动断开连接。
   *
   * @param exception I/O 异常。
   * @return true 表示异常可按客户端断开处理。
   */
  private boolean isClientDisconnected(IOException exception) {
    if (DisconnectedClientHelper.isClientDisconnectedException(exception)) {
      return true;
    }
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains(WINDOWS_CONNECTION_ABORTED_ZH) || text.contains(WINDOWS_CONNECTION_RESET_ZH)) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}



