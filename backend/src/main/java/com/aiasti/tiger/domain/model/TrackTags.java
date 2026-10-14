package com.aiasti.tiger.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 一首曲目的标签值（不可变）。
 *
 * <p>文本字段按 {@link TagField} 保存，封面以二进制保存并做防御性拷贝。
 * 所有修改操作返回新实例，适合作为 DEV-002 的「首次快照 / 当前编辑值」载体。
 */
public final class TrackTags {

    private final Map<TagField, String> values;
    private final byte[] cover;

    private TrackTags(Map<TagField, String> values, byte[] cover) {
        this.values = values;
        this.cover = cover;
    }

    public static TrackTags empty() {
        return new TrackTags(new EnumMap<>(TagField.class), null);
    }

    /** 返回设置或清除某字段后的新实例；空值表示清除该字段。 */
    public TrackTags withValue(TagField field, String value) {
        Objects.requireNonNull(field, "field");
        EnumMap<TagField, String> copy = new EnumMap<>(values);
        if (value == null || value.isEmpty()) {
            copy.remove(field);
        } else {
            copy.put(field, value);
        }
        return new TrackTags(copy, cover);
    }

    /** 返回替换封面后的新实例；传入数组会被拷贝，调用方后续修改不影响本对象。 */
    public TrackTags withCover(byte[] cover) {
        return new TrackTags(new EnumMap<>(values), cover == null ? null : cover.clone());
    }

    public Optional<String> value(TagField field) {
        return Optional.ofNullable(values.get(field));
    }

    public Map<TagField, String> values() {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }

    /** 返回封面的副本；无封面时返回 {@code null}。 */
    public byte[] cover() {
        return cover == null ? null : cover.clone();
    }

    public boolean hasCover() {
        return cover != null && cover.length > 0;
    }
}
