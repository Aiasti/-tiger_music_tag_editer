package com.aiasti.tiger.application.settings;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.domain.model.OnlineSettings;
import com.aiasti.tiger.domain.port.OnlineSettingsPort;

/**
 * 运行期在线设置用例（S5、BIZ-035）。
 *
 * <p>设置由 Electron 主进程持久化并经本地接口下发；此处只把更新交给端口实现，
 * 对后续请求生效，不写入任何配置文件。
 */
@Service
public class OnlineSettingsService {

    private final OnlineSettingsPort port;

    public OnlineSettingsService(OnlineSettingsPort port) {
        this.port = port;
    }

    public OnlineSettings snapshot() {
        return port.snapshot();
    }

    public void update(OnlineSettingsPort.Update update) {
        port.update(update);
    }
}
