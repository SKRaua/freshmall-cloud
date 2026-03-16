package com.freshmall.common.exception;

/**
 * 通用业务异常：用于向上层返回可预期、可提示的业务错误。
 */
public class BizException extends RuntimeException {

    private final String bizCode;

    public BizException(String message) {
        super(message);
        this.bizCode = "BIZ_ERROR";
    }

    public BizException(String bizCode, String message) {
        super(message);
        this.bizCode = bizCode;
    }

    public String getBizCode() {
        return bizCode;
    }
}
