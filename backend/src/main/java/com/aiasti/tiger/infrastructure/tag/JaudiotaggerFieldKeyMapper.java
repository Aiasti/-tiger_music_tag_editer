package com.aiasti.tiger.infrastructure.tag;

import com.aiasti.tiger.domain.model.TagField;
import org.jaudiotagger.tag.FieldKey;

import java.util.EnumMap;
import java.util.Map;

/** {@link TagField} 与 jaudiotagger {@link FieldKey} 的映射。 */
final class JaudiotaggerFieldKeyMapper {

    private static final Map<TagField, FieldKey> TO_LIBRARY = new EnumMap<>(TagField.class);

    static {
        TO_LIBRARY.put(TagField.TITLE, FieldKey.TITLE);
        TO_LIBRARY.put(TagField.ARTIST, FieldKey.ARTIST);
        TO_LIBRARY.put(TagField.ALBUM, FieldKey.ALBUM);
        TO_LIBRARY.put(TagField.ALBUM_ARTIST, FieldKey.ALBUM_ARTIST);
        TO_LIBRARY.put(TagField.TRACK, FieldKey.TRACK);
        TO_LIBRARY.put(TagField.DISC_NO, FieldKey.DISC_NO);
        TO_LIBRARY.put(TagField.YEAR, FieldKey.YEAR);
        TO_LIBRARY.put(TagField.GENRE, FieldKey.GENRE);
        TO_LIBRARY.put(TagField.COMPOSER, FieldKey.COMPOSER);
        TO_LIBRARY.put(TagField.LYRICIST, FieldKey.LYRICIST);
        TO_LIBRARY.put(TagField.ARRANGER, FieldKey.ARRANGER);
        TO_LIBRARY.put(TagField.COMMENT, FieldKey.COMMENT);
    }

    private JaudiotaggerFieldKeyMapper() {
    }

    static FieldKey toLibrary(TagField field) {
        return TO_LIBRARY.get(field);
    }
}
