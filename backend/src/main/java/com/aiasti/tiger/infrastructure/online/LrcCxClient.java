package com.aiasti.tiger.infrastructure.online;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.CoverCandidate;
import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;
import com.aiasti.tiger.domain.port.CoverSearchPort;
import com.aiasti.tiger.domain.port.LyricsPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * lrcapi 公开服务客户端（歌词与封面共用 /jsonapi）。
 *
 * <p>公开实例的 /api/v1 路径当前不可用，使用旧版 /jsonapi；实际歌词字段为 lrc。
 */
@Component
@ConditionalOnProperty(prefix = "app.online.lrc-cx", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LrcCxClient implements LyricsPort, CoverSearchPort {

    private static final Pattern TIMELINE = Pattern.compile("\\[\\d{1,2}:\\d{2}");
    private static final int MAX_CACHE_ENTRIES = 300;

    private final RestClient client;
    private final RequestThrottle throttle;
    private final String userAgent;
    private final ObjectMapper json;
    private final Map<String, List<LrcCxItem>> cache = new ConcurrentHashMap<>();

    public LrcCxClient(RestClient.Builder builder, OnlineProperties properties, ObjectMapper json) {
        OnlineProperties.LrcCx config = properties.getLrcCx();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(Math.max(5, config.getTimeoutSeconds()) * 1_000);
        this.client = builder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.throttle = new RequestThrottle(config.getRequestsPerSecond());
        this.userAgent = config.getUserAgent() == null ? "" : config.getUserAgent().trim();
        this.json = json;
    }

    @Override
    public String sourceName() {
        return "LRCAPI";
    }

    @Override
    public List<LyricsCandidate> search(LyricsQuery query) {
        String title = firstNonBlank(query.title(), query.keyword());
        List<LyricsCandidate> candidates = new ArrayList<>();
        for (LrcCxItem item : fetch(title, query.artist(), query.album())) {
            if (item.lrc().isBlank()) {
                continue;
            }
            candidates.add(new LyricsCandidate(
                    "lrc-cx-" + item.id(),
                    "LRCAPI",
                    item.title(),
                    item.artist(),
                    item.album(),
                    formatDuration(item.durationSeconds()),
                    TIMELINE.matcher(item.lrc()).find(),
                    LyricsConfidence.score(query, item.title(), item.artist(), item.album(),
                            item.durationSeconds()),
                    item.lrc()));
        }
        candidates.sort(Comparator.comparingInt(LyricsCandidate::confidence).reversed());
        return candidates;
    }

    @Override
    public List<CoverCandidate> search(String album, String artist, String title) {
        List<CoverCandidate> candidates = new ArrayList<>();
        for (LrcCxItem item : fetch(title, artist, album)) {
            if (item.coverUrl().isBlank()) {
                continue;
            }
            int score = LyricsConfidence.score(
                    new LyricsQuery(title, artist, album, "", item.durationSeconds()),
                    item.title(), item.artist(), item.album(), item.durationSeconds());
            candidates.add(new CoverCandidate(
                    "lrc-cx-" + item.id(),
                    "LRCAPI",
                    score,
                    item.title(),
                    item.artist(),
                    item.album(),
                    item.year(),
                    "",
                    "",
                    item.coverUrl(),
                    "LRCAPI · Apple Music 封面 · 600x600"));
        }
        candidates.sort(Comparator.comparingInt(CoverCandidate::score).reversed());
        return candidates;
    }

    private List<LrcCxItem> fetch(String title, String artist, String album) {
        if (title.isBlank() && artist.isBlank() && album.isBlank()) {
            return List.of();
        }
        String cacheKey = normalize(title) + "|" + normalize(artist) + "|" + normalize(album);
        List<LrcCxItem> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String body = execute(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/jsonapi")
                        .queryParam("title", title)
                        .queryParam("artist", artist)
                        .queryParam("album", album)
                        .build())
                .header(HttpHeaders.USER_AGENT, userAgent)
                .retrieve()
                .body(String.class));
        List<LrcCxItem> items = parse(body);
        if (!items.isEmpty()) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(cacheKey, items);
        }
        return items;
    }

    private List<LrcCxItem> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (java.io.IOException e) {
            throw new OnlineAccessException("解析 lrcapi 响应失败：" + e.getMessage(), e);
        }
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<LrcCxItem> items = new ArrayList<>();
        for (JsonNode item : root) {
            String id = firstNonBlank(text(item, "id"), text(item, "object_id"));
            String lrc = firstNonBlank(text(item, "lrc"), text(item, "lyrics"));
            String cover = artwork(item);
            if (id.isBlank() || (lrc.isBlank() && cover.isBlank())) {
                continue;
            }
            String releaseDate = text(item, "releaseDate");
            String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
            items.add(new LrcCxItem(
                    id,
                    text(item, "title"),
                    text(item, "artist"),
                    text(item, "album"),
                    item.path("duration").asLong(0),
                    year,
                    lrc,
                    cover));
        }
        return items;
    }

    private static String artwork(JsonNode item) {
        String format = text(item, "cover_format");
        if (!format.isBlank()) {
            return format.replace("{w}x{h}", "600x600")
                    .replace("{w}", "600")
                    .replace("{h}", "600");
        }
        String cover = text(item, "cover");
        if (cover.contains("mzstatic.com")) {
            return cover.replaceAll("\\d+x\\d+bb", "600x600bb");
        }
        if (cover.contains("music.126.net")) {
            // 网易云图床默认返回原始大图，需显式请求 600×600
            return cover + (cover.contains("?") ? "&" : "?") + "param=600y600";
        }
        return cover;
    }

    private String execute(java.util.function.Supplier<String> call) {
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
                throw new OnlineAccessException("lrcapi 请求失败：HTTP " + status, e);
            } catch (ResourceAccessException e) {
                if (attempt < 2) {
                    sleep(1_000L * (attempt + 1));
                    continue;
                }
                throw new OnlineAccessException("lrcapi 连接失败：" + e.getMessage(), e);
            }
        }
        throw new OnlineAccessException("lrcapi 请求失败");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OnlineAccessException("歌词检索被中断", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private record LrcCxItem(
            String id,
            String title,
            String artist,
            String album,
            long durationSeconds,
            String year,
            String lrc,
            String coverUrl) {
    }
}
