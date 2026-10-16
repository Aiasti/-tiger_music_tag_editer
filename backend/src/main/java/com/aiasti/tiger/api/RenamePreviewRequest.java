package com.aiasti.tiger.api;

import java.util.List;

import com.aiasti.tiger.application.workspace.RenameInput;

/**
 * 重命名/归档预览请求体（FR-7、BIZ-030）。
 *
 * @param mode         {@code rename} 仅改文件名 / {@code archive} 归档位置
 * @param nameTemplate 重命名文件名模板（如 {@code %track% %title%}）
 * @param dirTemplate  归档目录模板（如 {@code %albumArtist%/%album%}）
 * @param rootDir      归档目标根目录（{@code flat}/{@code template} 需要）
 * @param archiveMode  {@code keep} / {@code flat} / {@code template}
 * @param tracks       按界面顺序的曲目与模板字段
 */
public record RenamePreviewRequest(
        String mode,
        String nameTemplate,
        String dirTemplate,
        String rootDir,
        String archiveMode,
        List<RenameInput> tracks) {
}
