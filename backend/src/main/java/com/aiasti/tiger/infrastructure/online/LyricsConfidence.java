package com.aiasti.tiger.infrastructure.online;

import java.util.Locale;

import com.aiasti.tiger.domain.model.LyricsQuery;

/** 在线歌词候选的统一置信度启发式（LRCLIB、lrcapi 共用）。 */
final class LyricsConfidence {

    private LyricsConfidence() {
    }

    /** 标题精确优先，其次艺术家、专辑与时长接近度。 */
    static int score(LyricsQuery query, String title, String artist, String album, long durationSeconds) {
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
}
