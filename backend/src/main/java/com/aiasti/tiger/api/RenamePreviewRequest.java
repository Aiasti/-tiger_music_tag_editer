package com.aiasti.tiger.api;

import java.util.List;

import com.aiasti.tiger.application.workspace.RenameInput;

/**
 * 重命名预览请求体（FR-7、BIZ-029）：目标根目录（空表示保持各自当前目录）+ 按界面顺序的曲目与命名字段。
 */
public record RenamePreviewRequest(String rootDir, List<RenameInput> tracks) {
}
