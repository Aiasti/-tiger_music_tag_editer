package com.aiasti.tiger.infrastructure.online;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;
import com.sun.net.httpserver.HttpServer;

/** S5 验证：LRCLIB 注册客户端 App-Id 请求头契约。 */
class LrclibLyricsClientTest {

    @Test
    void sendsRegisteredAppIdHeader() throws Exception {
        AtomicReference<String> appId = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            try (exchange) {
                appId.set(exchange.getRequestHeaders().getFirst("Lrclib-App-Id"));
                userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                query.set(exchange.getRequestURI().getQuery());
                byte[] body = """
                        [{"id":1,"trackName":"Imagine","artistName":"John Lennon","albumName":"Imagine","duration":183,"syncedLyrics":"[00:01.00]Imagine","plainLyrics":"Imagine","instrumental":false}]
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            OnlineProperties properties = new OnlineProperties();
            OnlineProperties.Lrclib config = properties.getLyrics().getLrclib();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setAppId("app-id-123");
            config.setUserAgent("TestAgent/1.0 (test@example.com)");
            config.setRequestsPerSecond(2);

            LrclibLyricsClient client = new LrclibLyricsClient(RestClient.builder(), properties);
            List<LyricsCandidate> candidates = client.search(
                    new LyricsQuery("Imagine", "John Lennon", "Imagine", "", 183));

            assertEquals(1, candidates.size());
            assertEquals("app-id-123", appId.get());
            assertEquals("TestAgent/1.0 (test@example.com)", userAgent.get());
            assertFalse(query.get().contains("album_name"),
                    "LRCLIB 对 track_name + album_name 组合返回 503，请求不得携带 album_name");
        } finally {
            server.stop(0);
        }
    }
}
