package com.aiasti.tiger.domain.port;

import com.aiasti.tiger.domain.model.TrackTags;

import java.nio.file.Path;

/**
 * 音频标签读写端口。
 *
 * <p>由基础设施层实现（当前为 jaudiotagger 适配器）；domain 只依赖本接口，
 * 不感知具体标签库的文件格式与 API。
 */
public interface TagAccessPort {

    /** 是否支持该文件的音频格式。 */
    boolean supports(Path file);

    /** 读取文件现有标签；无标签时返回空值对象。 */
    TrackTags read(Path file);

    /** 将标签写回文件。 */
    void write(Path file, TrackTags tags);
}
