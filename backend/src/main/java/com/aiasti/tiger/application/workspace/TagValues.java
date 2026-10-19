package com.aiasti.tiger.application.workspace;

import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;

/** 供界面消费的标签值（契约字段与前端 `TrackTagData` 一致）。 */
public record TagValues(
        String title,
        String artist,
        String album,
        String albumArtist,
        String trackNo,
        String discNo,
        String year,
        String genre,
        String composer,
        String lyricist,
        String arranger,
        String comment,
        String lyrics) {

    public static TagValues of(TrackTags tags) {
        return new TagValues(
                tags.value(TagField.TITLE).orElse(""),
                tags.value(TagField.ARTIST).orElse(""),
                tags.value(TagField.ALBUM).orElse(""),
                tags.value(TagField.ALBUM_ARTIST).orElse(""),
                tags.value(TagField.TRACK).orElse(""),
                tags.value(TagField.DISC_NO).orElse(""),
                tags.value(TagField.YEAR).orElse(""),
                tags.value(TagField.GENRE).orElse(""),
                tags.value(TagField.COMPOSER).orElse(""),
                tags.value(TagField.LYRICIST).orElse(""),
                tags.value(TagField.ARRANGER).orElse(""),
                tags.value(TagField.COMMENT).orElse(""),
                tags.value(TagField.LYRICS).orElse(""));
    }
}
