package com.aiasti.tiger.infrastructure.online;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.CoverImage;
import com.aiasti.tiger.domain.port.CoverArtPort;

/**
 * Cover Art Archive 客户端（FR-6、DEV-004）。
 *
 * <p>优先按发行组取正面封面（front-500），失败再按发行版取；按 1 请求/秒串行并缓存结果。
 */
@Component
public class CoverArtArchiveClient implements CoverArtPort {

    private static final int MAX_CACHE_ENTRIES = 200;

    private final RestClient client;
    private final RequestThrottle throttle;
    private final Map<String, Optional<CoverImage>> cache = new ConcurrentHashMap<>();

    public CoverArtArchiveClient(RestClient.Builder builder, OnlineProperties properties) {
        OnlineProperties.CoverArt config = properties.getCoverArt();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(Math.max(5, config.getTimeoutSeconds()) * 1_000);
        this.client = builder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.throttle = new RequestThrottle(config.getRequestsPerSecond());
    }

    @Override
    public Optional<CoverImage> front(String releaseGroupId, String releaseId) {
        String group = releaseGroupId == null ? "" : releaseGroupId.trim();
        String release = releaseId == null ? "" : releaseId.trim();
        if (group.isBlank() && release.isBlank()) {
            return Optional.empty();
        }
        String key = group + "|" + release;
        Optional<CoverImage> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<CoverImage> image = Optional.empty();
        if (!group.isBlank()) {
            image = fetchPath("/release-group/" + group + "/front-500");
        }
        if (image.isEmpty() && !release.isBlank()) {
            image = fetchPath("/release/" + release + "/front-500");
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, image);
        return image;
    }

    @Override
    public Optional<CoverImage> fetch(String imageUrl) {
        Optional<URI> trusted = trustedImageUri(imageUrl);
        if (trusted.isEmpty()) {
            return Optional.empty();
        }
        URI uri = trusted.get();
        String key = "url|" + uri;
        Optional<CoverImage> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<CoverImage> image = fetchAbsolute(uri);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, image);
        return image;
    }

    /** 规范化并校验图片地址：仅允许可信主机；http 地址升级为 https。 */
    static Optional<URI> trustedImageUri(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (uri.getHost() == null) {
            return Optional.empty();
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            uri = URI.create(uri.toString().replaceFirst("(?i)^http://", "https://"));
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost(uri.getHost())) {
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    /** 只代理可信图片源，避免把后端变成任意 URL 代理。 */
    private static boolean trustedHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.toLowerCase(java.util.Locale.ROOT);
        return value.equals("mzstatic.com") || value.endsWith(".mzstatic.com")
                || value.equals("music.126.net") || value.endsWith(".music.126.net")
                || value.equals("kugou.com") || value.endsWith(".kugou.com")
                || value.equals("coverartarchive.org") || value.endsWith(".coverartarchive.org")
                || value.equals("archive.org") || value.endsWith(".archive.org");
    }

    private Optional<CoverImage> fetchAbsolute(URI uri) {
        for (int attempt = 0; attempt < 3; attempt++) {
            throttle.acquire();
            try {
                ResponseEntity<byte[]> response = client.get().uri(uri).retrieve().toEntity(byte[].class);
                return toImage(response);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 404) {
                    return Optional.empty();
                }
                if ((status == 429 || status == 503) && attempt < 2) {
                    sleep(1_000L * (attempt + 1));
                    continue;
                }
                return Optional.empty();
            } catch (ResourceAccessException e) {
                if (attempt < 2) {
                    sleep(1_000L * (attempt + 1));
                    continue;
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<CoverImage> toImage(ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        return Optional.of(new CoverImage(body,
                contentType == null || contentType.isBlank() ? "image/jpeg" : contentType));
    }

    private Optional<CoverImage> fetchPath(String path) {
        for (int attempt = 0; attempt < 2; attempt++) {
            throttle.acquire();
            try {
                ResponseEntity<byte[]> response = client.get().uri(path).retrieve().toEntity(byte[].class);
                return toImage(response);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 404) {
                    return Optional.empty();
                }
                if ((status == 429 || status == 503) && attempt < 1) {
                    sleep(1_000L);
                    continue;
                }
                throw new OnlineAccessException("封面请求失败：HTTP " + status, e);
            } catch (ResourceAccessException e) {
                if (attempt < 1) {
                    sleep(1_000L);
                    continue;
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
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
