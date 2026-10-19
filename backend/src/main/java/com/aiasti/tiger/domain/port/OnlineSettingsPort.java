package com.aiasti.tiger.domain.port;

import com.aiasti.tiger.domain.model.OnlineSettings;

/**
 * 在线服务设置端口（S5、BIZ-035）。
 *
 * <p>配置由桌面壳持久化并经本地接口下发；实现负责把设置写入运行期配置。
 */
public interface OnlineSettingsPort {

    OnlineSettings snapshot();

    /** 更新设置；字段为 {@code null} 表示保持不变，API Key 为空串表示显式清除。 */
    void update(Update update);

    record Update(String musicBrainzUserAgent, String acoustidApiKey, String fpcalcPath) {
    }
}
