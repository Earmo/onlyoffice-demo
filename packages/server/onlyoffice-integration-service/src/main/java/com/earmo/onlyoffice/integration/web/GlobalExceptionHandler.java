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

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, MAX_UPLOAD_MESSAGE, null, response);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), null, response);
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<?> handleDocumentNotFound(DocumentNotFoundException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), null, response);
  }

  @ExceptionHandler(DocumentOperationConflictException.class)
  public ResponseEntity<?> handleDocumentOperationConflict(DocumentOperationConflictException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.CONFLICT, exception.getMessage(), null, response);
  }

  @ExceptionHandler(AccessContextException.class)
  public ResponseEntity<?> handleAccessContextException(AccessContextException exception, HttpServletResponse response) {
    return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), null, response);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> handleIllegalState(IllegalStateException exception, HttpServletResponse response) {
    log.error("Internal state error", exception);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务端处理失败，请稍后重试。", null, response);
  }

  @ExceptionHandler(LlmApiException.class)
  public ResponseEntity<?> handleLlmApiException(LlmApiException exception, HttpServletResponse response) {
    return errorResponse(exception.httpStatus(), exception.getMessage(), exception.errorCode(), response);
  }

  @ExceptionHandler(AsyncRequestTimeoutException.class)
  public ResponseEntity<?> handleAsyncRequestTimeout(AsyncRequestTimeoutException exception, HttpServletResponse response) {
    log.warn("Async request timed out", exception);
    return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "流式响应超时，请稍后重试。", null, response);
  }

  @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
  public ResponseEntity<?> handleIoException(IOException exception, HttpServletResponse response) {
    if (isClientDisconnected(exception)) {
      log.warn("Client disconnected during response streaming: {}", exception.getMessage());
      return errorResponse(HttpStatus.NO_CONTENT, null, null, response);
    }
    log.error("I/O exception while writing response", exception);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务端处理失败，请稍后重试。", null, response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGenericException(Exception exception, HttpServletResponse response) {
    log.error("Unhandled server exception", exception);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务端处理失败，请稍后重试。", null, response);
  }

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



