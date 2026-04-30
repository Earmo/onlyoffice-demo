package com.earmo.onlyoffice.integration.exception;

import com.earmo.onlyoffice.integration.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 文档不存在时抛出的异常。
 */
public class DocumentNotFoundException extends BaseException {

    /**
     * 创建文档不存在异常。
     *
     * @param documentId 文档唯一标识
     */
    public DocumentNotFoundException(String documentId) {
        super("DOCUMENT_NOT_FOUND", "文档不存在：" + documentId, HttpStatus.NOT_FOUND);
    }
}


