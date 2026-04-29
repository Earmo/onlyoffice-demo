/**
 * Copyright 2017-2025 Evergrande Group.
 */
package com.earmo.onlyoffice.integration.common.constant;

/**
 * Common Constant
 *
 * @author gaoyanlong
 * @since 2018年1月6日
 */
public final class CommonConstant {

    private CommonConstant() {
    }

    // authentication & authorization
    public static final int JWT_MAX_AGE_MINUTES = 60 * 24 * 365;
    public static final int JWT_ONE_STEP_MINUTES = 30;
    public static final String TOKEN_COOKIE_NAME = "jwt-token";

    // error code
    public static final String SUCCESS_CODE = "00000";
    public static final String BIP_SUCCESS_CODE = "1000000000";
    public static final String DEFAULT_SYS_ERROR_CODE = "00099";
    public static final String DEFAULT_LOGOUT_ERROR_CODE = "00401";
    public static final String DEFAULT_FORBIDDEN_ERROR_CODE = "00403";
    public static final String DEFAULT_SYS_ERROR_MSG = "系统内部错误";

    // front_type
    public static final String HEADER_AUTH = "Authorization";
    public static final int LOGIN_VALID_SECOND = 86400;

    public static final String FEIGN_HEADER_TRACE_ID = "FEIGN_TRACE_ID";
    public static final String FEIGN_HEADER_USER_ID = "FEIGN_USER_ID";
    public static final String FEIGN_HEADER_USER_NAME = "FEIGN_USER_NAME";
    public static final String FEIGN_HEADER_USER_TYPE = "FEIGN_USER_TYPE";
    public static final String FEIGN_HEADER_FRONT_TYPE = "FEIGN_FRONT_TYPE";
    public static final String FEIGN_HEADER_IP = "FEIGN_IP";

    public static final String X_TRACE_ID = "X-Trace-Id";

    public static final String REGIONAL = "Regional";

    public static final String TRACE_ID = "trace_id";

    public static final String DEFAULT_COURT_UUID = "00000000000000000000000000000000";
}
