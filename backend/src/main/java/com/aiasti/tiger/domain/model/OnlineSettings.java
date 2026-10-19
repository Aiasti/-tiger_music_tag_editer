package com.aiasti.tiger.domain.model;

/**
 * 在线服务设置的脱敏快照（S5、BIZ-035）。
 *
 * <p>只暴露「是否已配置 API Key」，不携带 Key 明文。
 */
public record OnlineSettings(
        String musicBrainzUserAgent,
        boolean acoustidKeyConfigured,
        String fpcalcPath) {
}
