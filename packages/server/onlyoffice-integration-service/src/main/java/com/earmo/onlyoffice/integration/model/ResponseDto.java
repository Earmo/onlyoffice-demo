package com.earmo.onlyoffice.integration.model;

import com.earmo.onlyoffice.integration.common.constant.CommonConstant;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

@Setter
@ToString
public class ResponseDto<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = -3985057684684781735L;

    private String code;
    @Getter
    private T data;
    @Getter
    private String message;

    public String getCode() {
        return code == null ? "" : code.trim();
    }

    public ResponseDto(String code, T data, String message) {
        super();
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public ResponseDto(T data) {
        super();
        this.code = CommonConstant.SUCCESS_CODE;
        this.data = data;
        this.message = "";
    }

    public ResponseDto() {
        super();
    }
}
