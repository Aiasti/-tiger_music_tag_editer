package com.aiasti.tiger.infrastructure.online;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.CoverCandidate;
import com.aiasti.tiger.domain.port.CoverSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * iTunes Search API 封面源（FR-6、DEV-004）。
 *
 * <p>按专辑名/艺术家/标题检索专辑，取 600x600 封面；在 Cover Art Archive 不可达时仍能提供可用图片。
 */
@Component
public class ItunesCoverClient implements CoverSearchPort {

    private static final int MAX_CANDIDATES = 5;

    private final RestClient client;
    private final RequestThrottle throttle;
    private final String country;
    private final ObjectMapper json;
    private final Map<String, List<CoverCandidate>> cache = new ConcurrentHashMap<>();

    public ItunesCoverClient(RestClient.Builder builder, OnlineProperties properties, ObjectMapper json) {
        OnlineProperties.Itunes config = properties.getItunes();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(Math.max(5, config.getTimeoutSeconds()) * 1_000);
        this.client = builder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.throttle = new RequestThrottle(config.getRequestsPerSecond());
        this.country = config.getCountry() == null ? "" : config.getCountry().trim();
        this.json = json;
    }

    @Override
    public List<CoverCandidate> search(String album, String artist, String title) {
        String term = buildTerm(album, artist, title);
        if (term.isBlank()) {
            return List.of();
        }
        String cacheKey = normalize(term) + "|" + normalize(album) + "|" + normalize(artist);
        List<CoverCandidate> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String body = execute(() -> client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/search")
                            .queryParam("term", term)
                            .queryParam("entity", "album")
                            .queryParam("limit", MAX_CANDIDATES);
                    if (!country.isBlank()) {
                        builder.queryParam("country", country);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(String.class));
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (java.io.IOException e) {
            throw new OnlineAccessException("解析 iTunes 响应失败：" + e.getMessage(), e);
        }
        Map<String, CoverCandidate> byId = new LinkedHashMap<>();
        for (JsonNode result : root.path("results")) {
            String collectionId = result.path("collectionId").asText("");
            String collectionName = text(result, "collectionName");
            String artwork = text(result, "artworkUrl100");
            if (collectionId.isBlank() || collectionName.isBlank() || artwork.isBlank()) {
                continue;
            }
            String artistName = text(result, "artistName");
            String releaseDate = text(result, "releaseDate");
            String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
            CoverCandidate candidate = new CoverCandidate(
                    "itunes-" + collectionId,
                    "iTunes",
                    score(album, artist, title, collectionName, artistName),
                    collectionName,
                    artistName,
                    collectionName,
                    year,
                    "",
                    "",
                    largeArtwork(artwork),
                    "iTunes 专辑检索 · Apple Music 封面");
            byId.put(candidate.id(), candidate);
        }
        List<CoverCandidate> result = new ArrayList<>(byId.values());
        result.sort((a, b) -> Integer.compare(b.score(), a.score()));
        result = result.stream().limit(MAX_CANDIDATES).toList();
        if (!result.isEmpty()) {
            if (cache.size() >= 500) {
                cache.clear();
            }
            cache.put(cacheKey, result);
        }
        return result;
    }

    static String buildTerm(String album, String artist, String title) {
        String primary = firstNonBlank(album, title);
        if (primary.isBlank()) {
            return "";
        }
        return artist == null || artist.isBlank() ? primary : primary + " " + artist.trim();
    }

    static String largeArtwork(String artworkUrl) {
        return artworkUrl == null ? "" : artworkUrl.replace("100x100bb", "600x600bb");
    }

    static int score(String album, String artist, String title, String collectionName, String artistName) {
        int score = 55;
        String expectedAlbum = normalize(album);
        String actualAlbum = normalize(collectionName);
        if (!expectedAlbum.isBlank() && !actualAlbum.isBlank()) {
            if (expectedAlbum.equals(actualAlbum)) {
                score += 25;
            } else if (expectedAlbum.contains(actualAlbum) || actualAlbum.contains(expectedAlbum)) {
                score += 15;
            }
        }
        String expectedArtist = normalize(artist);
        String actualArtist = normalize(artistName);
        if (!expectedArtist.isBlank() && !actualArtist.isBlank()
                && (expectedArtist.contains(actualArtist) || actualArtist.contains(expectedArtist))) {
            score += 15;
        }
        String expectedTitle = normalize(title);
        if (!expectedTitle.isBlank() && !actualAlbum.isBlank()
                && (expectedTitle.equals(actualAlbum) || actualAlbum.contains(expectedTitle))) {
            score += 5;
        }
        return Math.min(99, score);
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

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
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
                throw new OnlineAccessException("iTunes 请求失败：HTTP " + status, e);
            } catch (ResourceAccessException e) {
                if (attempt < 2) {
                    sleep(1_000L * (attempt + 1));
                    continue;
                }
                throw new OnlineAccessException("iTunes 连接失败：" + e.getMessage(), e);
            }
        }
        throw new OnlineAccessException("iTunes 请求失败");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OnlineAccessException("封面检索被中断", e);
        }
    }
}
