package com.aiasti.tiger.infrastructure.online;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;
import com.aiasti.tiger.domain.port.LyricsPort;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * LRCLIB 歌词源（FR-6、DEV-004）。
 *
 * <p>免鉴权、支持同步 LRC 与纯文本；按标题/艺术家/专辑检索，并用标题、艺术家与时长计算置信度。
 */
@Component
public class LrclibLyricsClient implements LyricsPort {

    private static final int MAX_CANDIDATES = 5;

    private final RestClient client;
    private final RequestThrottle throttle;
    /** 会话内缓存：同一检索条件在批量/重复检索时不重复请求，减少 503。 */
    private final Map<String, List<LyricsCandidate>> cache = new ConcurrentHashMap<>();

    public LrclibLyricsClient(RestClient.Builder builder, OnlineProperties properties) {
        OnlineProperties.Lrclib config = properties.getLyrics().getLrclib();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(Math.max(3, config.getTimeoutSeconds()) * 1_000);
        this.client = builder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, config.getUserAgent())
                .defaultHeader("Lrclib-Client", config.getUserAgent())
                .build();
        this.throttle = new RequestThrottle(config.getRequestsPerSecond());
    }

    @Override
    public List<LyricsCandidate> search(LyricsQuery query) {
        String trackName = firstNonBlank(query.keyword(), query.title());
        if (trackName.isBlank() && query.artist().isBlank()) {
            return List.of();
        }
        String cacheKey = normalize(trackName) + "|" + normalize(query.artist()) + "|" + normalize(query.album());
        List<LyricsCandidate> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JsonNode root = execute(() -> client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/search");
                    if (!trackName.isBlank()) {
                        builder.queryParam("track_name", trackName);
                    }
                    if (query.artist() != null && !query.artist().isBlank()) {
                        builder.queryParam("artist_name", query.artist());
                    }
                    if (query.album() != null && !query.album().isBlank()) {
                        builder.queryParam("album_name", query.album());
                    }
                    if (trackName.isBlank() && query.keyword() != null && !query.keyword().isBlank()) {
                        builder.queryParam("q", query.keyword());
                    }
                    return builder.build();
                })
                .retrieve()
                .body(JsonNode.class));
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<LyricsCandidate> candidates = new ArrayList<>();
        for (JsonNode item : root) {
            String synced = text(item, "syncedLyrics");
            String plain = text(item, "plainLyrics");
            String preview = !synced.isBlank() ? synced : plain;
            if (preview.isBlank() || item.path("instrumental").asBoolean(false)) {
                continue;
            }
            String title = text(item, "trackName");
            String artist = text(item, "artistName");
            String album = text(item, "albumName");
            long duration = item.path("duration").asLong(0);
            candidates.add(new LyricsCandidate(
                    "lrclib-" + item.path("id").asText("0"),
                    "LRCLIB",
                    title,
                    artist,
                    album,
                    formatDuration(duration),
                    !synced.isBlank(),
                    confidence(query, title, artist, album, duration),
                    preview));
        }
        List<LyricsCandidate> result = candidates.stream()
                .sorted(Comparator.comparingInt(LyricsCandidate::confidence).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
        if (!result.isEmpty()) {
            if (cache.size() >= 500) {
                cache.clear();
            }
            cache.put(cacheKey, result);
        }
        return result;
    }
    /** 置信度启发式：标题精确优先，其次艺术家、专辑与时长接近度。 */
    static int confidence(LyricsQuery query, String title, String artist, String album, long durationSeconds) {
        int score = 50;
        String expectedTitle = normalize(firstNonBlank(query.title(), query.keyword()));
        String actualTitle = normalize(title);
        if (!expectedTitle.isBlank() && !actualTitle.isBlank()) {
            if (expectedTitle.equals(actualTitle)) {
                score += 25;
            } else if (expectedTitle.contains(actualTitle) || actualTitle.contains(expectedTitle)) {
                score += 10;
            }
        }
        if (containsNormalized(query.artist(), artist)) {
            score += 15;
        }
        if (containsNormalized(query.album(), album)) {
            score += 5;
        }
        if (query.durationSeconds() > 0 && durationSeconds > 0
                && Math.abs(query.durationSeconds() - durationSeconds) <= 3) {
            score += 5;
        }
        return Math.min(99, score);
    }

    private static boolean containsNormalized(String expected, String actual) {
        String a = normalize(expected);
        String b = normalize(actual);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private JsonNode execute(java.util.function.Supplier<JsonNode> call) {
        for (int attempt = 0; attempt < 4; attempt++) {
            throttle.acquire();
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status == 429 || status == 503) && attempt < 3) {
                    sleep(retryDelayMillis(e, attempt));
                    continue;
                }
                throw new OnlineAccessException("LRCLIB 请求失败：HTTP " + status, e);
            } catch (ResourceAccessException e) {
                throw new OnlineAccessException("LRCLIB 连接失败：" + e.getMessage(), e);
            }
        }
        throw new OnlineAccessException("LRCLIB 请求失败");
    }

    /** 优先使用 Retry-After；否则按 1s / 2s / 4s 指数退避。 */
    private static long retryDelayMillis(RestClientResponseException e, int attempt) {
        HttpHeaders headers = e.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Math.max(1, Long.parseLong(retryAfter.trim())) * 1000L;
            } catch (NumberFormatException ignored) {
                // 非秒数格式时使用指数退避
            }
        }
        return 1_000L << attempt;
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
