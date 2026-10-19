package com.aiasti.tiger.api;

/** 单曲元数据匹配请求（FR-5）：字段为渲染层当前编辑值，可为空。 */
public record MetadataMatchRequest(
        Long trackId,
        String title,
        String artist,
        String album,
        String albumArtist,
        String trackNo,
        String discNo,
        String year,
        String keyword) {
}
