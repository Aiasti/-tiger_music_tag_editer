package com.aiasti.tiger.infrastructure.tag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.StandardArtwork;
import org.jaudiotagger.tag.wav.WavTag;
import org.springframework.stereotype.Component;

import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

/**
 * 基于 jaudiotagger 的标签读写适配器。
 *
 * <p>是项目中唯一接触 jaudiotagger 的位置，把库的 API 转换为 {@link TrackTags}。
 * 已知限制：WAV 的标签写回在 jaudiotagger 3.0.1 下不生效（见 dev.md DEV-001）。
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
    public TrackTags read(Path file) {
        try {
            Tag tag = AudioFileIO.read(file.toFile()).getTag();
            if (tag == null) {
                return TrackTags.empty();
            }
            TrackTags result = TrackTags.empty();
            for (TagField field : TagField.values()) {
                String value = tag.getFirst(JaudiotaggerFieldKeyMapper.toLibrary(field));
                if (value != null && !value.isEmpty()) {
                    result = result.withValue(field, value);
                }
            }
            Artwork artwork = tag.getFirstArtwork();
            if (artwork != null && artwork.getBinaryData() != null) {
                result = result.withCover(artwork.getBinaryData());
            }
            return result;
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
