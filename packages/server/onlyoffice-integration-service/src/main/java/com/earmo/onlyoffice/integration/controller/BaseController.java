package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.common.constant.CommonConstant;
import com.earmo.onlyoffice.integration.common.exception.BaseException;
import com.earmo.onlyoffice.integration.common.exception.CommonException;
import com.earmo.onlyoffice.integration.model.ResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础Controller，AOP用
 */
public class BaseController {

    protected static final Logger logger = LoggerFactory.getLogger(BaseController.class);

    protected ResponseDto<Object> successResponse() {
        return new ResponseDto<>(CommonConstant.SUCCESS_CODE, null, null);
    }

    protected <T> ResponseDto<T> successResponseWithData(T data) {
        return new ResponseDto<>(CommonConstant.SUCCESS_CODE, data, null);
    }

    protected <T> ResponseDto<T> failedResponseWithData(T data, String message) {
        return new ResponseDto<>(CommonConstant.DEFAULT_SYS_ERROR_CODE, data, message);
    }

    @ExceptionHandler(CommonException.class)
    public ResponseDto<Object> handlerForCommonException(CommonException e) {
        logger.error("CommonException: ", e);
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(e.getCode());
        responseDto.setMessage(e.getMessage());
        return responseDto;
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ResponseDto<Object>> handlerForBaseException(BaseException e) {
        logger.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(e.getCode());
        responseDto.setMessage(e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(responseDto);
    }


    private ResponseDto<Object> globalErrorResult(String message) {
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage(message);
        return responseDto;
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseDto<Object> handlerForDataAccessException(DataAccessException e) {
        logger.error("DataAccessException: ", e);
        return globalErrorResult("数据库操作异常：" + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseDto<Object> handlerForException(Exception e) {
        logger.error("Exception: ", e);
        return globalErrorResult("未知错误：" + e.getMessage());
    }


    // 新增参数校验异常处理
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseDto<Object>> handlerForCommonException(IllegalArgumentException e) {
        logger.warn("非法参数异常: {}", e.getMessage());
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage(e.getMessage());
        return ResponseEntity.badRequest().body(responseDto);
    }

    // 参数转换异常处理
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseDto<Object>> handlerForIllegalStateException(IllegalStateException e) {
        logger.error("请求参数异常: ", e);
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage("服务端处理失败，请稍后重试。");
        return ResponseEntity.internalServerError().body(responseDto);
    }

    // 新增参数绑定异常处理（处理Spring框架层面的参数转换错误）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseDto<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.warn("参数转换异常: {}", ex.getMessage());
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage("请求参数格式错误：" + ex.getMessage());
        return responseDto;
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseDto<Object> handleBindingExceptions(Exception ex) {
        logger.warn("请求参数绑定异常: {}", ex.getMessage());
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage("请求参数异常：" + ex.getMessage());
        return responseDto;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ResponseDto<Object>> handlerRuntimeException(RuntimeException e) {
        logger.error("运行时异常: ", e);
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage(e.getMessage());
        return ResponseEntity.internalServerError().body(responseDto);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseDto<Object> handlerForMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        List<String> fieldResultList = new ArrayList<>();
        for (FieldError error : fieldErrors) {
            String defaultMessage = error.getDefaultMessage();
            fieldResultList.add(defaultMessage);
        }
        logger.error("参数校验异常: ", e);
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage("参数校验异常: " + String.join(";", fieldResultList));
        return responseDto;
    }

    // IO异常
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ResponseDto<Object>> handleIOException(IOException e) {
        logger.error("IO异常: ", e);
        ResponseDto<Object> responseDto = new ResponseDto<>();
        responseDto.setCode(CommonConstant.DEFAULT_SYS_ERROR_CODE);
        responseDto.setMessage("IO异常：" + e.getMessage());
        return ResponseEntity.internalServerError().body(responseDto);
    }
}
