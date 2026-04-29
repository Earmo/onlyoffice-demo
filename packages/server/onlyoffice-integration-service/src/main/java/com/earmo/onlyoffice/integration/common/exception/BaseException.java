package com.earmo.onlyoffice.integration.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 异常基类，各个模块的运行期异常均继承与该类
 */
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

    /**
     * @param code the code to set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * @return the message
     */
    @Override
    public String getMessage() {
        return super.getMessage();
    }

    /**
     * @param throwable the throwable to set
     */
    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    /**
     * @param values the values to set
     */
    public void setValues(Object[] values) {
        this.values = values;
    }

    public BaseException(String code, String message, Object[] values, Throwable cause) {
        this(code, message, cause);
        this.values = values;
    }

    private BaseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
