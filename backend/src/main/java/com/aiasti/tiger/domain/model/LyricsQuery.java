package com.aiasti.tiger.domain.model;

/**
 * 歌词检索条件（FR-6）。
 *
 * @param keyword         显式检索词；为空时由 title / artist 或文件名回退（BIZ-024）
 * @param durationSeconds 当前曲目时长，用于候选置信度评分；未知时为 0
 */
public record LyricsQuery(
        String title,
        String artist,
        String album,
        String keyword,
        long durationSeconds) {
}
