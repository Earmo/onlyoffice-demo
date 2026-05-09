package com.earmo.onlyoffice.integration.exception;

import com.earmo.onlyoffice.integration.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 文档处于冲突状态时抛出的异常。
 */
public class DocumentOperationConflictException extends BaseException {

    /**
     * 创建文档操作冲突异常。
     *
     * @param message 冲突说明
     */
    public DocumentOperationConflictException(String message) {
        super("DOCUMENT_OPERATION_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
