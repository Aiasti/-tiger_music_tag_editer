package com.aiasti.tiger.domain.model;

/**
 * 本地 Chromaprint 指纹（DEV-004）。
 *
 * @param durationSeconds 音频时长（秒，可为小数）
 * @param fingerprint     Chromaprint 压缩指纹；空串表示音频过短或无法生成
 */
public record Fingerprint(double durationSeconds, String fingerprint) {

    public boolean usable() {
        return fingerprint != null && !fingerprint.isBlank();
    }
}
