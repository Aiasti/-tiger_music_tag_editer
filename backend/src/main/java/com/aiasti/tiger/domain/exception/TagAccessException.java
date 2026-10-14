package com.aiasti.tiger.domain.exception;

/** 标签读取或写入失败。 */
public class TagAccessException extends RuntimeException {

    public TagAccessException(String message) {
        super(message);
    }

    public TagAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
