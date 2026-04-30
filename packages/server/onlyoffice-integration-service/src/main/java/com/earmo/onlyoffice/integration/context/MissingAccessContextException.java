package com.earmo.onlyoffice.integration.context;

/**
 * 用于表达“请求没有提供足够访问上下文”的 4xx 语义。
 */
public class MissingAccessContextException extends AccessContextException {

    public MissingAccessContextException(String message) {
        super(message);
    }
}
