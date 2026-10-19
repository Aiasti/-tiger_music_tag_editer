package com.aiasti.tiger.infrastructure.online;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.Fingerprint;
import com.aiasti.tiger.domain.model.MetadataCandidate;
import com.aiasti.tiger.domain.port.AcoustIdPort;
import com.aiasti.tiger.domain.port.FingerprintPort;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * AcoustID 指纹查询客户端（FR-5、DEV-004）。
 *
 * <p>本地用 fpcalc 生成 Chromaprint 指纹后提交查询，不上传原始音频；限制 3 请求/秒。
 */
@Component
public class AcoustIdClient implements AcoustIdPort {

    private final RestClient client;
    private final RequestThrottle throttle;
    private final FingerprintPort fingerprint;
    private final String apiKey;

    public AcoustIdClient(RestClient.Builder builder, OnlineProperties properties, FingerprintPort fingerprint) {
        OnlineProperties.AcoustId config = properties.getAcoustid();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.client = builder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.throttle = new RequestThrottle(config.getRequestsPerSecond());
        this.fingerprint = fingerprint;
        this.apiKey = config.getApiKey() == null ? "" : config.getApiKey().trim();
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank() && fingerprint.isAvailable();
    }

    @Override
    public String unavailableReason() {
        if (apiKey.isBlank()) {
            return "未配置 AcoustID API Key（环境变量 TIGER_ACOUSTID_KEY），已仅使用 MusicBrainz 文本检索";
        }
        if (!fingerprint.isAvailable()) {
            return "未找到 fpcalc（Chromaprint），无法生成音频指纹，已仅使用 MusicBrainz 文本检索";
        }
        return "";
    }

    @Override
    public List<MetadataCandidate> lookup(Path file) {
        if (!isAvailable()) {
            throw new OnlineAccessException(unavailableReason());
        }
        Optional<Fingerprint> generated = fingerprint.create(file);
        if (generated.isEmpty() || !generated.get().usable()) {
            throw new OnlineAccessException("无法生成音频指纹（音频过短或 fpcalc 失败）");
        }
        Fingerprint value = generated.get();
        long duration = Math.max(1, Math.round(value.durationSeconds()));
        String body = formBody(apiKey, duration, value.fingerprint());
        JsonNode root = execute(() -> client.post()
                .uri("/lookup")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        if (root == null) {
            return List.of();
        }
        if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
            String message = root.path("error").path("message").asText("AcoustID 返回错误");
            throw new OnlineAccessException("AcoustID 查询失败：" + message);
        }
        return toCandidates(root.path("results"));
    }

    private static List<MetadataCandidate> toCandidates(JsonNode results) {
        Map<String, MetadataCandidate> byRecording = new LinkedHashMap<>();
        for (JsonNode result : results) {
            int score = (int) Math.round(result.path("score").asDouble(0) * 100);
            for (JsonNode recording : result.path("recordings")) {
                String id = text(recording, "id");
                String title = text(recording, "title");
                if (id.isBlank() || title.isBlank()) {
                    continue;
                }
                String artist = joinArtistCredit(recording.path("artists"));
                JsonNode releaseGroup = bestReleaseGroup(recording.path("releasegroups"));
                String album = "";
                String albumArtist = artist;
                String year = "";
                String releaseId = "";
                String releaseGroupId = "";
                if (releaseGroup != null) {
                    album = text(releaseGroup, "title");
                    releaseGroupId = text(releaseGroup, "id");
                    String groupArtist = joinArtistCredit(releaseGroup.path("artists"));
                    if (!groupArtist.isBlank()) {
                        albumArtist = groupArtist;
                    }
                    JsonNode release = bestRelease(releaseGroup.path("releases"));
                    if (release != null) {
                        releaseId = text(release, "id");
                        String date = text(release, "date");
                        year = date.length() >= 4 ? date.substring(0, 4) : date;
                    }
                }
                MetadataCandidate candidate = new MetadataCandidate(
                        id,
                        "AcoustID",
                        score,
                        title,
                        artist,
                        album,
                        albumArtist,
                        "",
                        "",
                        year,
                        "",
                        releaseId,
                        releaseGroupId,
                        "Chromaprint 指纹 · AcoustID 匹配度 " + score + "%");
                MetadataCandidate existing = byRecording.get(id);
                byRecording.put(id, existing == null ? candidate : existing.merge(candidate));
            }
        }
        return new ArrayList<>(byRecording.values());
    }

    private static JsonNode bestReleaseGroup(JsonNode groups) {
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode group : groups) {
            int score = 0;
            String type = text(group, "type");
            if ("Album".equalsIgnoreCase(type) || "Single".equalsIgnoreCase(type) || "EP".equalsIgnoreCase(type)) {
                score += 10;
            }
            if (group.path("releases").isArray() && !group.path("releases").isEmpty()) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = group;
            }
        }
        return best;
    }

    private static JsonNode bestRelease(JsonNode releases) {
        if (releases == null || !releases.isArray() || releases.isEmpty()) {
            return null;
        }
        for (JsonNode release : releases) {
            if (!text(release, "date").isBlank()) {
                return release;
            }
        }
        return releases.get(0);
    }

    private static String joinArtistCredit(JsonNode artists) {
        if (artists == null || !artists.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : artists) {
            text.append(text(item, "name"));
            text.append(text(item, "joinphrase"));
        }
        return text.toString().trim();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 构造 AcoustID 表单体。
     *
     * <p>meta 必须使用字面量 {@code +} 分隔；若编码为 {@code %2B}，服务端会返回 results 但 recordings 为空。
     */
    static String formBody(String apiKey, long duration, String fingerprint) {
        return "client=" + encode(apiKey)
                + "&duration=" + duration
                + "&meta=recordings+releasegroups+releases"
                + "&fingerprint=" + encode(fingerprint);
    }

    private JsonNode execute(java.util.function.Supplier<JsonNode> call) {
        for (int attempt = 0; attempt < 3; attempt++) {
            throttle.acquire();
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status == 429 || status == 503) && attempt < 2) {
                    sleep(1_000L * (attempt + 1));
                    continue;
                }
                throw new OnlineAccessException("AcoustID 请求失败：HTTP " + status, e);
            } catch (ResourceAccessException e) {
                throw new OnlineAccessException("AcoustID 连接失败：" + e.getMessage(), e);
            }
        }
        throw new OnlineAccessException("AcoustID 请求失败");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OnlineAccessException("在线检索被中断", e);
        }
    }
}
