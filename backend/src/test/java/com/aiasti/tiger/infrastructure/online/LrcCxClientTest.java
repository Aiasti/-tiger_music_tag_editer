package com.aiasti.tiger.infrastructure.online;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.aiasti.tiger.domain.model.CoverCandidate;
import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** lrcapi /jsonapi 协议契约：路径、字段兼容、600×600 封面与 429 重试。 */
class LrcCxClientTest {

    @Test
    void parsesLrcAndRequests600Cover() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jsonapi", exchange -> {
            try (exchange) {
                path.set(exchange.getRequestURI().getPath());
                query.set(exchange.getRequestURI().getQuery());
                userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                respond(exchange, 200, """
                        [
                          {"id":"a1","title":"Imagine","artist":"John Lennon","album":"Imagine",
                           "duration":183,"releaseDate":"1971-09-09",
                           "lrc":"[00:01.00]Imagine all the people",
                           "cover":"https://is1-ssl.mzstatic.com/image/thumb/a1/1500x1500bb.jpg",
                           "cover_format":"https://is1-ssl.mzstatic.com/image/{w}x{h}bb.jpg"},
                          {"id":"a2","title":"Imagine","artist":"John Lennon","album":"Imagine",
                           "duration":183,"lrc":"","cover":"https://p2.music.126.net/abc/def.jpg"},
                          {"id":"a3","title":"Imagine","artist":"John Lennon","album":"Imagine",
                           "duration":183,"lrc":"","cover":"https://is1-ssl.mzstatic.com/image/thumb/a3/3000x3000bb.jpg"}
                        ]
                        """);
            }
        });
        server.start();
        try {
            LrcCxClient client = client(server);

            List<LyricsCandidate> lyrics = client.search(
                    new LyricsQuery("Imagine", "John Lennon", "Imagine", "", 183));
            List<CoverCandidate> covers = client.search("Imagine", "John Lennon", "Imagine");

            assertEquals("/jsonapi", path.get(), "公开实例只提供旧版 /jsonapi 路径");
            assertTrue(query.get().contains("title=Imagine"), query.get());
            assertTrue(query.get().contains("artist=John"), query.get());
            assertTrue(userAgent.get().startsWith("TestAgent/1.0"), userAgent.get());

            assertEquals(1, lyrics.size(), "空歌词条目应被过滤");
            assertEquals("[00:01.00]Imagine all the people", lyrics.get(0).preview());
            assertEquals("LRCAPI", lyrics.get(0).source());
            assertTrue(lyrics.get(0).hasTimeline());

            assertEquals(3, covers.size(), "有封面即可成为封面候选，不要求歌词非空");
            assertEquals("https://is1-ssl.mzstatic.com/image/600x600bb.jpg",
                    covers.get(0).imageUrl(), "cover_format 模板应替换为 600×600");
            assertEquals("https://p2.music.126.net/abc/def.jpg?param=600y600",
                    covers.get(1).imageUrl(), "网易云封面应追加 600×600 参数");
            assertEquals("https://is1-ssl.mzstatic.com/image/thumb/a3/600x600bb.jpg",
                    covers.get(2).imageUrl(), "mzstatic 原始大图应替换为 600x600bb");
            assertEquals("1971", covers.get(0).year());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesAfterTooManyRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jsonapi", exchange -> {
            try (exchange) {
                if (calls.incrementAndGet() == 1) {
                    respond(exchange, 429, "{}");
                    return;
                }
                respond(exchange, 200, """
                        [{"id":"a1","title":"Song","artist":"Artist","album":"Album",
                          "duration":200,"lrc":"[00:01.00]line",
                          "cover_format":"https://is1-ssl.mzstatic.com/image/{w}x{h}bb.jpg"}]
                        """);
            }
        });
        server.start();
        try {
            List<LyricsCandidate> candidates = client(server).search(
                    new LyricsQuery("Song", "Artist", "Album", "", 200));

            assertEquals(1, candidates.size());
            assertEquals(2, calls.get(), "429 后应重试");
        } finally {
            server.stop(0);
        }
    }

    private static LrcCxClient client(HttpServer server) {
        OnlineProperties properties = new OnlineProperties();
        OnlineProperties.LrcCx config = properties.getLrcCx();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setUserAgent("TestAgent/1.0 (test@example.com)");
        config.setRequestsPerSecond(50);
        config.setTimeoutSeconds(5);
        return new LrcCxClient(RestClient.builder(), properties, new ObjectMapper());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
