package com.aiasti.tiger.infrastructure.online;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** 封面图片代理地址校验：可信主机、http 升级 https 与非法地址拒绝。 */
class CoverArtArchiveClientTest {

    @Test
    void upgradesTrustedHttpImageToHttps() {
        Optional<URI> uri = CoverArtArchiveClient.trustedImageUri(
                "http://p1.music.126.net/ZGffiDQZrGj5s_hnR1CNbg==/109951165566379710.jpg");

        assertTrue(uri.isPresent());
        assertEquals("https", uri.get().getScheme());
        assertEquals("p1.music.126.net", uri.get().getHost());
    }

    @Test
    void acceptsKugouHosts() {
        assertTrue(CoverArtArchiveClient.trustedImageUri(
                "https://imge.kugou.com/stdmusic/20200101/cover.jpg").isPresent());
    }

    @Test
    void rejectsUnknownHosts() {
        assertTrue(CoverArtArchiveClient.trustedImageUri(
                "https://example.com/cover.jpg").isEmpty());
        assertTrue(CoverArtArchiveClient.trustedImageUri("not-a-url").isEmpty());
    }
}
