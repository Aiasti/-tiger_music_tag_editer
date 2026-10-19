package com.aiasti.tiger.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aiasti.tiger.application.settings.OnlineSettingsService;
import com.aiasti.tiger.domain.model.OnlineSettings;
import com.aiasti.tiger.domain.port.OnlineSettingsPort;

/**
 * 在线服务设置的运行期读写（S5、BIZ-035）。
 *
 * <p>由 Electron 主进程在启动或保存首选项时调用；GET 不回显 API Key 明文。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OnlineSettingsService settingsService;

    public SettingsController(OnlineSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/online")
    public OnlineSettingsResponse getOnline() {
        return OnlineSettingsResponse.from(settingsService.snapshot());
    }

    @PutMapping("/online")
    public OnlineSettingsResponse updateOnline(@RequestBody OnlineSettingsRequest request) {
        try {
            settingsService.update(new OnlineSettingsPort.Update(
                    request.musicBrainzUserAgent(),
                    request.acoustidApiKey(),
                    request.fpcalcPath()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return OnlineSettingsResponse.from(settingsService.snapshot());
    }

    /** 请求字段为 {@code null} 表示保持不变；API Key 为空串表示清除。 */
    public record OnlineSettingsRequest(
            String musicBrainzUserAgent,
            String acoustidApiKey,
            String fpcalcPath) {
    }

    /** 脱敏响应：不包含 API Key。 */
    public record OnlineSettingsResponse(
            String musicBrainzUserAgent,
            boolean acoustidKeyConfigured,
            String fpcalcPath) {

        static OnlineSettingsResponse from(OnlineSettings settings) {
            return new OnlineSettingsResponse(
                    settings.musicBrainzUserAgent(),
                    settings.acoustidKeyConfigured(),
                    settings.fpcalcPath());
        }
    }
}
