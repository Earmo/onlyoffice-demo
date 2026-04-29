package com.earmo.onlyoffice.integration.common.exception;

import com.earmo.onlyoffice.integration.common.constant.CommonConstant;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;

/**
 * Common Exception
 */
public class CommonException extends BaseException {
    @Serial
    private static final long serialVersionUID = -4527567935254966321L;

    public CommonException(String message) {
        super(CommonConstant.DEFAULT_SYS_ERROR_CODE, message, null, null);
    }

    public CommonException(String code, String message) {
        super(code, message, null, null);
    }

    public CommonException(String code, String message, Object[] values) {
        super(code, message, values, null);
    }

    public CommonException(String code, String message, Object[] values, Throwable cause) {
        super(code, message, values, cause);
    }

    public String getErrorCode() {
        String errorCode = CommonConstant.DEFAULT_SYS_ERROR_CODE;
        String message = getMessage() == null ? getCode() : getMessage();
        if (StringUtils.isNotBlank(message)) {
            String[] messageValues = StringUtils.split(message, ":");
            if (messageValues.length > 1) {
                errorCode = messageValues[0];
            }
        }
        return errorCode;
    }

}
