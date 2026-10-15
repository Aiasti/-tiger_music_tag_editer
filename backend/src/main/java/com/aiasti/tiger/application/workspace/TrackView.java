package com.aiasti.tiger.application.workspace;

/** 供界面消费的曲目视图（契约字段与前端 `TrackItem` 一致）。 */
public record TrackView(
        long id,
        String filename,
        String ext,
        String filePath,
        String size,
        String duration,
        boolean hasCover,
        String status,
        String failureReason,
        TagValues tags,
        TagValues initialSnapshot) {
}
