package com.earmo.onlyoffice.integration.exception;

/**
 * 用于表达“访问上下文格式错误或解析失败”的 4xx 语义。
 */
public class InvalidAccessContextException extends AccessContextException {

    public InvalidAccessContextException(String message) {
        super(message);
    }

    public InvalidAccessContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
