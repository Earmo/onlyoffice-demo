package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.context.AccessContextException;
import com.earmo.onlyoffice.integration.model.ApiErrorResponse;
import com.earmo.onlyoffice.integration.service.DocumentNotFoundException;
import com.earmo.onlyoffice.integration.service.DocumentOperationConflictException;
import com.earmo.onlyoffice.integration.service.llm.LlmApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 统一处理接口异常，避免把框架异常直接暴露给前端。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String MAX_UPLOAD_MESSAGE = "上传文件超过大小限制，当前最大允许 50MB。";

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(new ApiErrorResponse(MAX_UPLOAD_MESSAGE));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleDocumentNotFound(DocumentNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(DocumentOperationConflictException.class)
  public ResponseEntity<ApiErrorResponse> handleDocumentOperationConflict(DocumentOperationConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(AccessContextException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessContextException(AccessContextException exception) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
    log.error("Internal state error", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiErrorResponse("服务端处理失败，请稍后重试。"));
  }

  @ExceptionHandler(LlmApiException.class)
  public ResponseEntity<ApiErrorResponse> handleLlmApiException(LlmApiException exception) {
    return ResponseEntity.status(exception.httpStatus())
        .body(new ApiErrorResponse(exception.getMessage(), exception.errorCode()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGenericException(Exception exception) {
    log.error("Unhandled server exception", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiErrorResponse("服务端处理失败，请稍后重试。"));
  }
}



