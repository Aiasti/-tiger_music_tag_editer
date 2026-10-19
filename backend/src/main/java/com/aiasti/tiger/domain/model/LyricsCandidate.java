package com.aiasti.tiger.domain.model;

/**
 * 在线歌词候选（FR-6、DEV-004）。
 *
 * @param preview 歌词正文：有 LRC 时间轴时优先使用同步歌词，否则为纯文本
 */
public record LyricsCandidate(
        String id,
        String source,
        String title,
        String artist,
        String album,
        String duration,
        boolean hasTimeline,
        int confidence,
        String preview) {
}
