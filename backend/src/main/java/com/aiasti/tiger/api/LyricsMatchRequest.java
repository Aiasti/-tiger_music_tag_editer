package com.aiasti.tiger.api;

/** 单曲歌词检索请求（FR-6）：字段为渲染层当前编辑值，可为空。 */
public record LyricsMatchRequest(
        Long trackId,
        String title,
        String artist,
        String album,
        String keyword) {
}
