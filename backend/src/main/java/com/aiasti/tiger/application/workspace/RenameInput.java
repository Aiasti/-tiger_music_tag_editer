package com.aiasti.tiger.application.workspace;

/**
 * 重命名/归档预览的单曲输入（BIZ-030）：模板所需字段来自渲染层当前编辑值。
 *
 * <p>字段与占位符对应：{@code trackNo→%track%}、{@code title→%title%}、{@code artist→%artist%}、
 * {@code albumArtist→%albumArtist%}、{@code album→%album%}、{@code discNo→%disc%}、{@code year→%year%}。
 */
public record RenameInput(
        long id,
        String trackNo,
        String title,
        String artist,
        String albumArtist,
        String album,
        String discNo,
        String year) {
}
