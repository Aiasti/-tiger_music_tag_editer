package com.aiasti.tiger.domain.model;

/**
 * 一次读取得到的音频元数据（标签 + 音频头信息）。
 *
 * @param tags            标签值（含内嵌封面）
 * @param durationSeconds 音频时长（秒）；无法获取时为 0
 */
public record AudioMetadata(TrackTags tags, long durationSeconds) {
}
