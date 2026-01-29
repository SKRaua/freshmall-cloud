package com.freshmall.common;


import java.io.Serializable;

import lombok.Data;

/**
 * @功能描述: 响应报文，统一封装类
 */
@Data
public class APIResponse<T> implements Serializable {
    private int code;
    private String msg;
    private T data;
    private String trace;
    private long timestamp;

    public APIResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public APIResponse(ResponseCode responseCode) {
        this.timestamp = System.currentTimeMillis();
        this.code = responseCode.getCode();
        this.msg = responseCode.getMsg();
    }

    public APIResponse(ResponseCode responseCode, String msg) {
        this(responseCode);
        this.msg = msg;
    }

    public APIResponse(ResponseCode responseCode, T data) {
        this(responseCode);
        this.data = data;
    }

    public APIResponse(ResponseCode responseCode, String msg, T data) {
        this(responseCode);
        this.trace = msg;
        this.data = data;
    }

    @Override
    public String toString() {
        return "APIResponse{" +
                "timestamp=" + timestamp +
                ", code=" + code +
                ", msg='" + msg + '\'' +
                ", trace='" + trace + '\'' +
                ", data=" + data +
                //", count=" + count +
                '}';
    }
}
