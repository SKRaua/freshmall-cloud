package com.freshmall.order.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public APIResponse handleBizException(BizException ex) {
        return new APIResponse(ResponseCode.FAIL, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public APIResponse handleIllegalArgumentException(IllegalArgumentException ex) {
        return new APIResponse(ResponseCode.FAIL, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public APIResponse handleException(Exception ex) {
        logger.error("order服务未处理异常", ex);
        return new APIResponse(ResponseCode.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");
    }
}
