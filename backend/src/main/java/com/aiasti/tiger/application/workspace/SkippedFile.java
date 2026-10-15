package com.aiasti.tiger.application.workspace;

/** 导入时被跳过的音频文件及可读原因（FR-1、BIZ-014）。 */
public record SkippedFile(String filename, String path, String reason) {
}
