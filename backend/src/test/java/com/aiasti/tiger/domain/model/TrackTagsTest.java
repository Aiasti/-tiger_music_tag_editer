package com.aiasti.tiger.domain.model;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** S0 验证：快照值对象的不可变与值隔离（DEV-002、BIZ-006）。 */
class TrackTagsTest {

    @Test
    void withValueDoesNotMutateOriginal() {
        TrackTags original = TrackTags.empty().withValue(TagField.TITLE, "A");

        TrackTags edited = original.withValue(TagField.TITLE, "B");

        assertEquals(Optional.of("A"), original.value(TagField.TITLE));
        assertEquals(Optional.of("B"), edited.value(TagField.TITLE));
    }

    @Test
    void emptyValueClearsField() {
        TrackTags tags = TrackTags.empty()
                .withValue(TagField.ARTIST, "X")
                .withValue(TagField.ARTIST, "");

        assertTrue(tags.value(TagField.ARTIST).isEmpty());
    }

    @Test
    void coverIsDefensivelyCopiedInAndOut() {
        byte[] source = {1, 2, 3};
        TrackTags tags = TrackTags.empty().withCover(source);

        source[0] = 9;
        assertEquals(9, source[0], "确认传入数组已被外部修改");
        assertEquals(1, tags.cover()[0], "外部修改原数组不应影响快照");

        byte[] leaked = tags.cover();
        leaked[1] = 9;
        assertEquals(9, leaked[1], "确认返回值已被外部修改");
        assertEquals(2, tags.cover()[1], "修改返回值不应影响快照内部数据");
    }

    @Test
    void valuesMapIsUnmodifiable() {
        TrackTags tags = TrackTags.empty().withValue(TagField.TITLE, "A");

        Throwable thrown = assertThrows(UnsupportedOperationException.class, () -> tags.values().clear());
        assertEquals(UnsupportedOperationException.class, thrown.getClass());
    }
}
