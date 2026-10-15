package com.aiasti.tiger.infrastructure.tag;

import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.AudioMetadata;
import com.aiasti.tiger.domain.model.ImageMimeTypes;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.StandardArtwork;
import org.jaudiotagger.tag.wav.WavTag;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 jaudiotagger 的标签读写适配器。
 *
 * <p>是项目中唯一接触 jaudiotagger 的位置，把库的 API 转换为 {@link TrackTags} 与 {@link AudioMetadata}。
 * 已知限制：WAV 的标签写回需先挂 ID3 子标签（见 dev.md DEV-001）。
 */
@Component
public class JaudiotaggerAudioTagAdapter implements TagAccessPort {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("mp3", "m4a", "mp4", "flac", "ogg", "wav");

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1));
    }

    @Override
    public AudioMetadata read(Path file) {
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            return new AudioMetadata(readTags(audioFile.getTag()), durationSeconds(audioFile));
        } catch (CannotReadException | IOException | TagException
                 | ReadOnlyFileException | InvalidAudioFrameException e) {
            throw new TagAccessException("读取标签失败: " + file, e);
        }
    }

    @Override
    public void write(Path file, TrackTags tags) {
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            activateId3TagForWav(tag);
            for (TagField field : TagField.values()) {
                FieldKey key = JaudiotaggerFieldKeyMapper.toLibrary(field);
                String value = tags.value(field).orElse(null);
                if (value == null) {
                    tag.deleteField(key);
                } else {
                    tag.setField(key, value);
                }
            }
            if (tags.hasCover()) {
                byte[] cover = tags.cover();
                StandardArtwork artwork = new StandardArtwork();
                artwork.setBinaryData(cover);
                artwork.setMimeType(ImageMimeTypes.detect(cover));
                tag.setField(artwork);
            }
            audioFile.commit();
        } catch (CannotReadException | IOException | TagException
                 | ReadOnlyFileException | InvalidAudioFrameException | CannotWriteException e) {
            throw new TagAccessException("写入标签失败: " + file, e);
        }
    }

    private static TrackTags readTags(Tag tag) {
        if (tag == null) {
            return TrackTags.empty();
        }
        TrackTags result = TrackTags.empty();
        for (TagField field : TagField.values()) {
            String value = readFieldQuietly(tag, JaudiotaggerFieldKeyMapper.toLibrary(field));
            if (value != null && !value.isEmpty()) {
                result = result.withValue(field, value);
            }
        }
        Artwork artwork = tag.getFirstArtwork();
        if (artwork != null && artwork.getBinaryData() != null) {
            result = result.withCover(artwork.getBinaryData());
        }
        return result;
    }

    private static long durationSeconds(AudioFile audioFile) {
        try {
            if (audioFile.getAudioHeader() == null) {
                return 0;
            }
            return Math.max(0, audioFile.getAudioHeader().getTrackLength());
        } catch (RuntimeException e) {
            // 部分容器无法给出时长：按未知处理，不影响标签读取
            return 0;
        }
    }

    /**
     * 读取单个字段；容器或标签体系不支持该字段时返回 {@code null}。
     *
     * <p>jaudiotagger 在部分格式（如 WAV 的 RIFF INFO）上对不支持的字段会抛出
     * {@code KeyNotFoundException} / {@code UnsupportedOperationException} 等运行时异常，
     * 这里按「无此字段」处理，避免单字段导致整文件读取失败。
     */
    private static String readFieldQuietly(Tag tag, FieldKey key) {
        try {
            return tag.getFirst(key);
        } catch (KeyNotFoundException | UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * WAV 无 ID3 chunk 时，挂一个默认 ID3 子标签并标记其存在；否则 WavTag 会把字段
     * 写入能力受限的 RIFF INFO 且写回不落盘。
     */
    private static void activateId3TagForWav(Tag tag) {
        if (tag instanceof WavTag wavTag && !wavTag.isExistingId3Tag()) {
            wavTag.setID3Tag(WavTag.createDefaultID3Tag());
            wavTag.setExistingId3Tag(true);
        }
    }
}
