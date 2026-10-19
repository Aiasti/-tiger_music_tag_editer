package com.aiasti.tiger.domain.exception;

/** 在线服务访问失败（网络、限流、响应解析或服务不可用）。 */
public class OnlineAccessException extends RuntimeException {

    public OnlineAccessException(String message) {
        super(message);
    }

    public OnlineAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
