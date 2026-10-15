package com.aiasti.tiger.infrastructure.tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;

/**
 * S0 验证：jaudiotagger 适配器对 PRD 支持格式的标签读写覆盖。
 *
 * <p>样本由 ffmpeg 生成（见 src/test/resources/samples）。结论：MP3/M4A(AAC)/ALAC/FLAC/OGG/WAV
 * 全字段可读写；WAV 由适配器挂默认 ID3 子标签后写回生效（见 JaudiotaggerAudioTagAdapter）。
 */
class JaudiotaggerAudioTagAdapterTest {

    private static final Path SAMPLES = Path.of("src", "test", "resources", "samples");

    private final JaudiotaggerAudioTagAdapter adapter = new JaudiotaggerAudioTagAdapter();

    @SuppressWarnings("unused") // 由 @MethodSource 反射调用，静态分析看不到引用
    static Stream<Arguments> supportedSamples() {
        return Stream.of(
                Arguments.of("mp3", "sample.mp3"),
                Arguments.of("m4a-aac", "sample-aac.m4a"),
                Arguments.of("m4a-alac", "sample-alac.m4a"),
                Arguments.of("flac", "sample.flac"),
                Arguments.of("ogg", "sample.ogg"),
                Arguments.of("wav", "sample.wav"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedSamples")
    void writeThenReadBack(String format, String fileName, @TempDir Path tempDir) throws Exception {
        Path work = copySample(fileName, tempDir);

        TrackTags expected = sampleTags();
        adapter.write(work, expected);

        TrackTags actual = adapter.read(work).tags();
        for (TagField field : TagField.values()) {
            assertEquals(expected.value(field), actual.value(field), format + " / " + field);
        }
    }

    /** 读取应同时给出音频时长（mp3 样本为 0.5 秒，至少可解析出非负值）。 */
    @Test
    void readReturnsDuration(@TempDir Path tempDir) throws Exception {
        Path work = copySample("sample.mp3", tempDir);

        long seconds = adapter.read(work).durationSeconds();

        assertTrue(seconds >= 0, "时长应可解析且非负");
    }

    /** 回归：读取未打标签的原始样本不应抛异常（WAV 的 RIFF INFO 不支持部分字段）。BUG-003。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedSamples")
    void readsUntaggedSampleWithoutFailing(String format, String fileName, @TempDir Path tempDir) throws Exception {
        Path work = copySample(fileName, tempDir);

        TrackTags tags = adapter.read(work).tags();

        assertNotNull(tags, format + " 读取不应返回 null");
    }

    @Test
    void writesAndReadsCover(@TempDir Path tempDir) throws Exception {
        Path work = copySample("sample.mp3", tempDir);
        byte[] cover = Files.readAllBytes(SAMPLES.resolve("cover.png"));

        adapter.write(work, sampleTags().withCover(cover));

        TrackTags actual = adapter.read(work).tags();
        assertTrue(actual.hasCover(), "应读回封面");
        assertArrayEquals(cover, actual.cover());
    }

    @Test
    void supportsKnownExtensions() {
        assertTrue(adapter.supports(Path.of("a.mp3")));
        assertTrue(adapter.supports(Path.of("A.FLAC")));
        assertFalse(adapter.supports(Path.of("a.txt")));
        assertFalse(adapter.supports(Path.of("noextension")));
    }

    private static Path copySample(String fileName, Path tempDir) throws Exception {
        Path work = tempDir.resolve(fileName);
        Files.copy(SAMPLES.resolve(fileName), work, StandardCopyOption.REPLACE_EXISTING);
        return work;
    }

    private static TrackTags sampleTags() {
        TrackTags tags = TrackTags.empty();
        tags = tags.withValue(TagField.TITLE, "Tiger Title");
        tags = tags.withValue(TagField.ARTIST, "Tiger Artist");
        tags = tags.withValue(TagField.ALBUM, "Tiger Album");
        tags = tags.withValue(TagField.ALBUM_ARTIST, "Tiger Album Artist");
        tags = tags.withValue(TagField.TRACK, "7");
        tags = tags.withValue(TagField.DISC_NO, "2");
        tags = tags.withValue(TagField.YEAR, "2026");
        tags = tags.withValue(TagField.GENRE, "Rock");
        tags = tags.withValue(TagField.COMPOSER, "Tiger Composer");
        tags = tags.withValue(TagField.LYRICIST, "Tiger Lyricist");
        tags = tags.withValue(TagField.ARRANGER, "Tiger Arranger");
        tags = tags.withValue(TagField.COMMENT, "Tiger Comment");
        return tags;
    }
}
