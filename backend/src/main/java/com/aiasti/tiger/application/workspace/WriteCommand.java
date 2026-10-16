package com.aiasti.tiger.application.workspace;

/**
 * 写回命令（BIZ-028、BIZ-030）：单首曲目的整份当前标签值与可选的目标路径。
 *
 * <p>渲染层提交整份值，后端按自己持有的「最近落盘值」基线自算差异并写入。
 * S2-2 只写 12 个元数据字段与封面；{@code tags.lyrics()} 暂不写回（歌词帧映射属 DEV-005）。
 *
 * @param id         曲目 id（工作区内稳定标识）
 * @param tags       当前编辑值（12 个元数据字段；lyrics 字段暂被忽略）
 * @param cover      封面意图：{@code null} 保持现状、空串移除封面、否则为 {@code data:} URL
 * @param targetPath 待写回的目标路径（重命名/归档产生）；{@code null} 或与当前相同表示不改路径
 */
public record WriteCommand(long id, TagValues tags, String cover, String targetPath) {
}
