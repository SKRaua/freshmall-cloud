package com.freshmall.thing.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public APIResponse handleBizException(BizException ex) {
        return new APIResponse(ResponseCode.FAIL, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public APIResponse handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.warn("上传文件超限: {}", ex.getMessage());
        return new APIResponse(ResponseCode.FAIL, "图片过大，单张图片最大支持5MB");
    }

    @ExceptionHandler(IllegalStateException.class)
    public APIResponse handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("FileSizeLimitExceededException")) {
            logger.warn("上传文件超限(IllegalState): {}", message);
            return new APIResponse(ResponseCode.FAIL, "图片过大，单张图片最大支持5MB");
        }
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public APIResponse handleException(Exception ex) {
        logger.error("thing服务未处理异常", ex);
        return new APIResponse(ResponseCode.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");
    }
}
