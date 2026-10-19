package com.aiasti.tiger.application.online;

/**
 * 单曲元数据匹配条件；字段来自渲染层当前编辑值，为空时后端回退到工作区最近落盘值或文件名。
 *
 * @param keyword 显式检索词；为空时按标题，再回退到去扩展名的文件名（BIZ-024）
 */
public record TrackMatchQuery(
        long trackId,
        String title,
        String artist,
        String album,
        String albumArtist,
        String trackNo,
        String discNo,
        String year,
        String keyword) {

    public static TrackMatchQuery of(long trackId, String title, String artist, String album,
                                     String albumArtist, String trackNo, String discNo,
                                     String year, String keyword) {
        return new TrackMatchQuery(trackId, title, artist, album, albumArtist, trackNo, discNo, year, keyword);
    }
}
