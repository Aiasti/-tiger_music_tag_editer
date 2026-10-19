package com.aiasti.tiger.domain.model;

/**
 * 在线元数据候选（FR-5、DEV-004）。
 *
 * <p>字段与前端 `MetadataCandidateItem` 对应；空串表示该来源没有提供此字段，应用候选时不应清空工作区已有值。
 */
public record MetadataCandidate(
        String id,
        String source,
        int score,
        String title,
        String artist,
        String album,
        String albumArtist,
        String trackNo,
        String discNo,
        String year,
        String genre,
        String releaseId,
        String releaseGroupId,
        String detail) {

    /** 同一录音同时被 MusicBrainz 与 AcoustID 命中时合并：保留已有字段、取较高分并标注双来源。 */
    public MetadataCandidate merge(MetadataCandidate other) {
        return new MetadataCandidate(
                id,
                mergeSource(source, other.source),
                Math.max(score, other.score),
                prefer(title, other.title),
                prefer(artist, other.artist),
                prefer(album, other.album),
                prefer(albumArtist, other.albumArtist),
                prefer(trackNo, other.trackNo),
                prefer(discNo, other.discNo),
                prefer(year, other.year),
                prefer(genre, other.genre),
                prefer(releaseId, other.releaseId),
                prefer(releaseGroupId, other.releaseGroupId),
                joinDetail(detail, other.detail));
    }

    private static String prefer(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }

    private static String mergeSource(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank() || first.contains(second)) {
            return first;
        }
        if (second.contains(first)) {
            return second;
        }
        return first + " + " + second;
    }

    private static String joinDetail(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "；" + second;
    }
}
