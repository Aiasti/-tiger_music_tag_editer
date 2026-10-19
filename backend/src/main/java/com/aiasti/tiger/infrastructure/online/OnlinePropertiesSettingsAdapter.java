package com.aiasti.tiger.infrastructure.online;

import org.springframework.stereotype.Component;

import com.aiasti.tiger.domain.model.OnlineSettings;
import com.aiasti.tiger.domain.port.OnlineSettingsPort;

/**
 * 把在线设置写入运行期的 {@link OnlineProperties}（S5、BIZ-035）。
 *
 * <p>不持久化：持久化由 Electron 主进程负责，后端进程重启后由桌面壳重新下发。
 */
@Component
public class OnlinePropertiesSettingsAdapter implements OnlineSettingsPort {

    private final OnlineProperties properties;

    public OnlinePropertiesSettingsAdapter(OnlineProperties properties) {
        this.properties = properties;
    }

    @Override
    public OnlineSettings snapshot() {
        return new OnlineSettings(
                properties.getMusicbrainz().getUserAgent(),
                !properties.getAcoustid().getApiKey().isBlank(),
                properties.getFpcalc().getPath());
    }

    @Override
    public synchronized void update(Update update) {
        if (update.musicBrainzUserAgent() != null) {
            String userAgent = update.musicBrainzUserAgent().trim();
            if (userAgent.isBlank()) {
                throw new IllegalArgumentException("MusicBrainz User-Agent 不能为空");
            }
            if (userAgent.length() > 300) {
                throw new IllegalArgumentException("MusicBrainz User-Agent 过长");
            }
            properties.getMusicbrainz().setUserAgent(userAgent);
        }
        if (update.acoustidApiKey() != null) {
            String apiKey = update.acoustidApiKey().trim();
            if (apiKey.length() > 200) {
                throw new IllegalArgumentException("AcoustID API Key 过长");
            }
            properties.getAcoustid().setApiKey(apiKey);
        }
        if (update.fpcalcPath() != null) {
            String path = update.fpcalcPath().trim();
            if (path.length() > 500) {
                throw new IllegalArgumentException("fpcalc 路径过长");
            }
            properties.getFpcalc().setPath(path.isBlank() ? "fpcalc" : path);
        }
    }
}
