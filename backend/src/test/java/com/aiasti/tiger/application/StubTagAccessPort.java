package com.aiasti.tiger.application;

import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 测试用标签端口：按扩展名判定是否支持；文件名以 {@code bad} 开头时模拟读取失败。
 */
class StubTagAccessPort implements TagAccessPort {

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
    public TrackTags read(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith("bad")) {
            throw new TagAccessException("读取标签失败: " + file, new IllegalStateException("corrupt"));
        }
        return TrackTags.empty()
                .withValue(TagField.TITLE, name)
                .withValue(TagField.ARTIST, "Stub Artist");
    }

    @Override
    public void write(Path file, TrackTags tags) {
        throw new UnsupportedOperationException("S1 不涉及写回");
    }
}
