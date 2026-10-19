package com.aiasti.tiger.application.online;

/**
 * 单曲歌词检索条件；字段来自渲染层当前编辑值，为空时后端回退到工作区值或文件名。
 */
public record LyricsMatchQuery(
        long trackId,
        String title,
        String artist,
        String album,
        String keyword) {
}
