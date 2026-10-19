package com.aiasti.tiger.domain.model;

/** PRD FR-4 中的文本标签字段（含 DEV-005 内嵌歌词）。 */
public enum TagField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    TRACK,
    DISC_NO,
    YEAR,
    GENRE,
    COMPOSER,
    LYRICIST,
    ARRANGER,
    COMMENT,
    /** 内嵌歌词（DEV-005）：按容器映射到 USLT / ©lyr / LYRICS 等帧。 */
    LYRICS
}
