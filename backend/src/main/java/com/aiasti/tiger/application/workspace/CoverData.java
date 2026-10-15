package com.aiasti.tiger.application.workspace;

/** 内嵌封面数据及其 MIME 类型（BIZ-015）。 */
public record CoverData(byte[] data, String mimeType) {
}
