package com.aiasti.tiger.infrastructure.online;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.MetadataCandidate;
import com.aiasti.tiger.domain.port.MusicBrainzPort;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * MusicBrainz Web Service 客户端（FR-5、DEV-004）。
 *
 * <p>遵守 1 请求/秒与必带 User-Agent 的要求；遇到 429/503 按 Retry-After 或指数退避重试。
 */
@Component
public class MusicBrainzClient implements MusicBrainzPort {

    private final RestClient client;
    private final RequestThrottle throttle;
    private final OnlineProperties properties;

    public MusicBrainzClient(RestClient.Builder builder, OnlineProperties properties) {
        OnlineProperties.MusicBrainz config = properties.getMusicbrainz();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        this.client = builder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.throttle = new RequestThrottle(config.getRequestsPerSecond());
        this.properties = properties;
    }

    /** User-Agent 可在运行期通过设置接口更新，因此每次请求读取当前配置（S5、BIZ-035）。 */
    private String userAgent() {
        return properties.getMusicbrainz().getUserAgent();
    }

    @Override
    public List<MetadataCandidate> search(String title, String artist, String album, String keyword) {
        String query = buildQuery(title, artist, keyword);
        if (query.isBlank()) {
            return List.of();
        }
        JsonNode root = execute(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/recording")
                        .queryParam("query", query)
                        .queryParam("fmt", "json")
                        .queryParam("limit", 5)
                        .build())
                .header(HttpHeaders.USER_AGENT, userAgent())
                .retrieve()
                .body(JsonNode.class));
        if (root == null) {
            return List.of();
        }
        List<MetadataCandidate> candidates = new ArrayList<>();
        for (JsonNode recording : root.path("recordings")) {
            MetadataCandidate candidate = toCandidate(recording, album);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /** 按专辑名 + 艺术家检索发行版，供封面匹配在录音检索无结果时回退。 */
    @Override
    public List<MetadataCandidate> searchReleases(String album, String artist) {
        if ((album == null || album.isBlank()) && (artist == null || artist.isBlank())) {
            return List.of();
        }
        StringBuilder query = new StringBuilder();
        if (album != null && !album.isBlank()) {
            query.append("release:\"").append(escape(album)).append('"');
        }
        if (artist != null && !artist.isBlank()) {
            if (query.length() > 0) {
                query.append(" AND ");
            }
            query.append("artist:\"").append(escape(artist)).append('"');
        }
        JsonNode root = execute(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/release")
                        .queryParam("query", query.toString())
                        .queryParam("fmt", "json")
                        .queryParam("limit", 5)
                        .build())
                .header(HttpHeaders.USER_AGENT, userAgent())
                .retrieve()
                .body(JsonNode.class));
        if (root == null) {
            return List.of();
        }
        List<MetadataCandidate> candidates = new ArrayList<>();
        for (JsonNode release : root.path("releases")) {
            String releaseId = text(release, "id");
            String title = text(release, "title");
            if (releaseId.isBlank() || title.isBlank()) {
                continue;
            }
            String artistText = joinArtistCredit(release.path("artist-credit"));
            String releaseGroupId = text(release.path("release-group"), "id");
            String type = text(release.path("release-group"), "primary-type");
            String date = text(release, "date");
            String year = date.length() >= 4 ? date.substring(0, 4) : date;
            List<String> parts = new ArrayList<>();
            parts.add("MusicBrainz 专辑检索");
            if (!type.isBlank()) {
                parts.add("类型 " + type);
            }
            if (!date.isBlank()) {
                parts.add(date);
            }
            int trackCount = release.path("track-count").asInt(0);
            if (trackCount > 0) {
                parts.add(trackCount + " 首");
            }
            candidates.add(new MetadataCandidate(
                    "release-" + releaseId,
                    "MusicBrainz",
                    release.path("score").asInt(0),
                    title,
                    artistText,
                    title,
                    artistText,
                    "",
                    "",
                    year,
                    "",
                    releaseId,
                    releaseGroupId,
                    String.join(" · ", parts)));
        }
        return candidates;
    }

    private MetadataCandidate toCandidate(JsonNode recording, String album) {
        String id = text(recording, "id");
        String title = text(recording, "title");
        if (id.isBlank() || title.isBlank()) {
            return null;
        }
        String artist = joinArtistCredit(recording.path("artist-credit"));
        JsonNode release = bestRelease(recording.path("releases"), album);
        String albumTitle = "";
        String albumArtist = "";
        String releaseId = "";
        String releaseGroupId = "";
        String year = text(recording, "first-release-date");
        if (year.length() > 4) {
            year = year.substring(0, 4);
        }
        String trackNo = "";
        String discNo = "";
        List<String> parts = new ArrayList<>();
        parts.add("MusicBrainz 文本匹配");
        if (release != null) {
            albumTitle = text(release, "title");
            albumArtist = joinArtistCredit(release.path("artist-credit"));
            releaseId = text(release, "id");
            releaseGroupId = text(release.path("release-group"), "id");
            String releaseDate = text(release, "date");
            if (!releaseDate.isBlank()) {
                year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : releaseDate;
                parts.add(releaseDate);
            }
            String status = text(release, "status");
            if (!status.isBlank()) {
                parts.add(status);
            }
            String type = text(release.path("release-group"), "primary-type");
            if (!type.isBlank()) {
                parts.add("类型 " + type);
            }
            int trackCount = release.path("track-count").asInt(0);
            if (trackCount > 0) {
                parts.add(trackCount + " 首");
            }
            String country = text(release, "country");
            if (!country.isBlank()) {
                parts.add(country);
            }
            JsonNode medium = firstMediumWithTrack(release.path("media"));
            if (medium != null) {
                discNo = text(medium, "position");
                JsonNode tracks = medium.path("track");
                if (tracks.isArray() && !tracks.isEmpty()) {
                    trackNo = text(tracks.get(0), "number");
                }
            }
        } else {
            parts.add("仅录音信息");
        }
        int score = recording.path("score").asInt(0);
        return new MetadataCandidate(
                id,
                "MusicBrainz",
                score,
                title,
                artist,
                albumTitle,
                albumArtist.isBlank() ? artist : albumArtist,
                trackNo,
                discNo,
                year,
                "",
                releaseId,
                releaseGroupId,
                String.join(" · ", parts));
    }

    /** 选择与当前专辑一致、状态为 Official 且类型明确的发行版；没有发行版时返回 null。 */
    private static JsonNode bestRelease(JsonNode releases, String album) {
        if (releases == null || !releases.isArray() || releases.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode release : releases) {
            int score = 0;
            String releaseTitle = text(release, "title");
            if (album != null && !album.isBlank() && releaseTitle.equalsIgnoreCase(album.trim())) {
                score += 100;
            }
            if ("Official".equalsIgnoreCase(text(release, "status"))) {
                score += 10;
            }
            String type = text(release.path("release-group"), "primary-type");
            if ("Album".equalsIgnoreCase(type) || "Single".equalsIgnoreCase(type) || "EP".equalsIgnoreCase(type)) {
                score += 5;
            }
            if (!text(release, "date").isBlank()) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = release;
            }
        }
        return best;
    }

    private static JsonNode firstMediumWithTrack(JsonNode media) {
        if (media == null || !media.isArray()) {
            return null;
        }
        for (JsonNode medium : media) {
            JsonNode tracks = medium.path("track");
            if (tracks.isArray() && !tracks.isEmpty()) {
                return medium;
            }
        }
        return media.isEmpty() ? null : media.get(0);
    }

    /** 把 artist-credit 按 joinphrase 拼成展示文本。 */
    static String joinArtistCredit(JsonNode credit) {
        if (credit == null || !credit.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : credit) {
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

    /** Lucene 查询：优先标题 + 艺术家，无标题时回退到文件名/关键词（BIZ-024）。 */
    static String buildQuery(String title, String artist, String keyword) {
        StringBuilder query = new StringBuilder();
        if (title != null && !title.isBlank()) {
            query.append("recording:\"").append(escape(title)).append('"');
            if (artist != null && !artist.isBlank()) {
                query.append(" AND artist:\"").append(escape(artist)).append('"');
            }
        } else if (keyword != null && !keyword.isBlank()) {
            query.append("recording:\"").append(escape(keyword)).append('"');
        }
        return query.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JsonNode execute(java.util.function.Supplier<JsonNode> call) {
        for (int attempt = 0; attempt < 3; attempt++) {
            throttle.acquire();
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status == 429 || status == 503) && attempt < 2) {
                    sleep(retryDelayMillis(e, attempt));
                    continue;
                }
                throw new OnlineAccessException("MusicBrainz 请求失败：HTTP " + status, e);
            } catch (ResourceAccessException e) {
                throw new OnlineAccessException("MusicBrainz 连接失败：" + e.getMessage(), e);
            }
        }
        throw new OnlineAccessException("MusicBrainz 请求失败");
    }

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
        return 1_000L * (attempt + 1);
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
