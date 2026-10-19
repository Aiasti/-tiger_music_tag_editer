package com.aiasti.tiger.infrastructure.online;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * S5 验证：fpcalc 路径在运行时设置变更后重新解析（BIZ-035）。
 */
class FpcalcFingerprintServiceTest {

    @Test
    void reResolvesWhenConfiguredPathChanges() throws IOException {
        Path tool = Files.createTempFile("fpcalc", ".exe");
        try {
            OnlineProperties properties = new OnlineProperties();
            FpcalcFingerprintService service = new FpcalcFingerprintService(properties, new ObjectMapper());

            properties.getFpcalc().setPath(tool.toString());
            assertTrue(service.isAvailable());

            properties.getFpcalc().setPath(tool + "-missing");
            assertFalse(service.isAvailable());

            properties.getFpcalc().setPath(tool.toString());
            assertTrue(service.isAvailable());
        } finally {
            Files.deleteIfExists(tool);
        }
    }
}
