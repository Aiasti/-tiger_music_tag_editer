package com.aiasti.tiger.domain.model;

/**
 * 在线封面候选（FR-6、DEV-004）。
 *
 * <p>封面匹配只提供封面图片，不携带任何歌词字段；应用封面不修改标签文本。
 * {@code imageUrl} 为可直接代理下载的图片地址（iTunes 等源）；Cover Art Archive 候选使用发行 ID。
 */
public record CoverCandidate(
        String id,
        String source,
        int score,
        String title,
        String artist,
        String album,
        String year,
        String releaseId,
        String releaseGroupId,
        String imageUrl,
        String detail) {
}
