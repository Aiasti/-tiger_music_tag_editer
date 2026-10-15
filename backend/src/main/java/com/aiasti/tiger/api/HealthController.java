package com.aiasti.tiger.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 就绪探测端点（BIZ-013）。
 *
 * <p>供 Electron 判断后端是否可用、打包期等待启动完成；不需要访问 token。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
