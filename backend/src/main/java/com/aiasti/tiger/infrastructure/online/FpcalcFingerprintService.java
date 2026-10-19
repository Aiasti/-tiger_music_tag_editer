package com.aiasti.tiger.infrastructure.online;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.Fingerprint;
import com.aiasti.tiger.domain.port.FingerprintPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 通过 Chromaprint CLI（fpcalc）生成音频指纹（DEV-004）。
 *
 * <p>fpcalc 路径来自配置；默认在 PATH 中查找。指纹按「路径 + 大小 + mtime」缓存，避免重复计算。
 */
@Component
public class FpcalcFingerprintService implements FingerprintPort {

    private static final Logger log = LoggerFactory.getLogger(FpcalcFingerprintService.class);
    private static final int MAX_CACHE_ENTRIES = 500;

    private final Path executable;
    private final int timeoutSeconds;
    private final ObjectMapper json;
    private final Map<String, Optional<Fingerprint>> cache = new ConcurrentHashMap<>();

    public FpcalcFingerprintService(OnlineProperties properties, ObjectMapper json) {
        this.executable = resolve(properties.getFpcalc().getPath());
        this.timeoutSeconds = Math.max(3, properties.getFpcalc().getTimeoutSeconds());
        this.json = json;
        if (executable == null) {
            log.warn("未找到 fpcalc，AcoustID 指纹查询将不可用（配置路径：{}）", properties.getFpcalc().getPath());
        }
    }

    @Override
    public boolean isAvailable() {
        return executable != null;
    }

    @Override
    public Optional<Fingerprint> create(Path file) {
        if (executable == null || file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String key = cacheKey(file);
        Optional<Fingerprint> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<Fingerprint> value = compute(file);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, value);
        return value;
    }

    private Optional<Fingerprint> compute(Path file) {
        Process process;
        try {
            process = new ProcessBuilder(executable.toString(), "-json", file.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new OnlineAccessException("无法启动 fpcalc：" + e.getMessage(), e);
        }

        CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> readAll(process));
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new OnlineAccessException("指纹生成超时（" + timeoutSeconds + " 秒）");
            }
            byte[] stdout = output.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                // fpcalc 对过短音频会以非 0 退出并输出 ERROR；按「无法生成指纹」处理，不影响其他字段
                return Optional.empty();
            }
            JsonNode root = json.readTree(stdout);
            String fingerprint = root.path("fingerprint").asText("");
            if (fingerprint.isBlank()) {
                return Optional.empty();
            }
            double duration = root.path("duration").asDouble(0);
            return Optional.of(new Fingerprint(duration, fingerprint));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OnlineAccessException("指纹生成被中断", e);
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            process.destroyForcibly();
            throw new OnlineAccessException("读取 fpcalc 输出失败：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new OnlineAccessException("解析 fpcalc 输出失败：" + e.getMessage(), e);
        }
    }

    private static byte[] readAll(Process process) {
        try (InputStream in = process.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private static String cacheKey(Path file) {
        try {
            Path absolute = file.toAbsolutePath().normalize();
            return absolute + "|" + Files.size(absolute) + "|" + Files.getLastModifiedTime(absolute).toMillis();
        } catch (IOException e) {
            return file.toAbsolutePath().normalize().toString();
        }
    }

    /** 解析可执行文件：绝对/相对路径直接检查；裸文件名在 PATH 中查找。 */
    static Path resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String value = configured.trim();
        if (value.contains("/") || value.contains("\\") || value.contains(":")) {
            Path path = Path.of(value);
            return Files.isRegularFile(path) ? path : null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            for (String name : new String[] { value, value + ".exe" }) {
                Path candidate = Path.of(entry, name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
