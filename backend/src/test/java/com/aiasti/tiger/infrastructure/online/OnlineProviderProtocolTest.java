package com.aiasti.tiger.infrastructure.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** S4 验证：Provider 查询构造与关键协议细节（FR-5、FR-6、DEV-004）。 */
class OnlineProviderProtocolTest {

    @Test
    void musicBrainzQueryUsesTitleAndArtist() {
        String query = MusicBrainzClient.buildQuery("Tiger Song", "Tiger Band", "fallback");

        assertEquals("recording:\"Tiger Song\" AND artist:\"Tiger Band\"", query);
    }

    @Test
    void musicBrainzQueryFallsBackToKeyword() {
        String query = MusicBrainzClient.buildQuery("", "", "01 Tiger Song");

        assertEquals("recording:\"01 Tiger Song\"", query);
    }

    @Test
    void musicBrainzQueryEscapesQuotes() {
        String query = MusicBrainzClient.buildQuery("Say \"Hello\"", "", "");

        assertEquals("recording:\"Say \\\"Hello\\\"\"", query);
    }

    @Test
    void acoustIdFormKeepsLiteralMetaSeparators() {
        String body = AcoustIdClient.formBody("key", 283, "AQADtEmi");

        assertTrue(body.contains("meta=recordings+releasegroups+releases"), body);
        assertFalse(body.contains("%2B"), "meta 分隔符不能被编码为 %2B，否则 AcoustID 不返回 recordings");
        assertTrue(body.contains("duration=283"), body);
        assertTrue(body.contains("fingerprint=AQADtEmi"), body);
    }

    @Test
    void lyricsConfidenceRewardsTitleAndDurationMatch() {
        int exact = LyricsConfidence.score(
                new com.aiasti.tiger.domain.model.LyricsQuery("Imagine", "John Lennon", "Imagine", "", 183),
                "Imagine", "John Lennon", "Imagine", 183);
        int different = LyricsConfidence.score(
                new com.aiasti.tiger.domain.model.LyricsQuery("Imagine", "John Lennon", "Imagine", "", 183),
                "Another Song", "Other Artist", "Other Album", 300);

        assertTrue(exact >= 95, "标题/艺术家/专辑/时长一致时置信度应较高");
        assertTrue(different < exact, "不匹配候选置信度应更低");
    }
}
