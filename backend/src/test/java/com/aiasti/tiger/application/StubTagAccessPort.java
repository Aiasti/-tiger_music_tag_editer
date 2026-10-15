package com.aiasti.tiger.application;

import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.AudioMetadata;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 测试用标签端口：按扩展名判定是否支持；文件名以 {@code bad} 开头时模拟读取失败；
 * 固定返回 125 秒时长与内嵌封面，便于断言视图字段。
 */
class StubTagAccessPort implements TagAccessPort {

    private static final long DURATION_SECONDS = 125;
    private static final byte[] COVER = {0x01, 0x02, 0x03};

    private final Set<String> extensions;

    StubTagAccessPort(String... extensions) {
        this.extensions = Set.of(extensions);
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot + 1));
    }

    @Override
    public AudioMetadata read(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith("bad")) {
            throw new TagAccessException("读取标签失败: " + file, new IllegalStateException("corrupt"));
        }
        TrackTags tags = TrackTags.empty()
                .withValue(TagField.TITLE, name)
                .withValue(TagField.ARTIST, "Stub Artist")
                .withCover(COVER);
        return new AudioMetadata(tags, DURATION_SECONDS);
    }

    @Override
    public void write(Path file, TrackTags tags) {
        throw new UnsupportedOperationException("S1 不涉及写回");
    }
}
