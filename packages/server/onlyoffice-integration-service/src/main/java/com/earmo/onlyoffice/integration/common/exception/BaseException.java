package com.earmo.onlyoffice.integration.common.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * 异常基类，各个模块的运行期异常均继承与该类
 */
@Setter
@Getter
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1381325479896057076L;

    /**
     * message key
     */
    private String code;

    /**
     * exception detail information
     */
    private Throwable throwable;

    /**
     * message params
     */
    private transient Object[] values;

    private HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    /**
     * @return the message
     */
    @Override
    public String getMessage() {
        return super.getMessage();
    }

    public BaseException(String code, String message, Object[] values, Throwable cause) {
        this(code, message, cause);
        this.values = values;
    }

    public BaseException(String code, String message) {
        this(code, message, null, HttpStatus.BAD_REQUEST);
    }

    public BaseException(String code, String message, Throwable cause) {
        this(code, message, cause, HttpStatus.BAD_REQUEST);
    }

    public BaseException(String code, String message, HttpStatus httpStatus) {
        this(code, message, null, httpStatus);
    }

    public BaseException(String code, String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus == null ? HttpStatus.BAD_REQUEST : httpStatus;
    }

}
