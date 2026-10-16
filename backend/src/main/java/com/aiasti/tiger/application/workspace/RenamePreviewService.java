package com.aiasti.tiger.application.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 重命名/归档预览用例（FR-7、BIZ-029）：按渲染层当前编辑值规划目标路径，不触碰磁盘。
 *
 * <p>S3-1 只产出规划与冲突标注；实际移动/重命名由 S3-2 执行。
 */
@Service
public class RenamePreviewService {

    private final Workspace workspace;

    public RenamePreviewService(Workspace workspace) {
        this.workspace = workspace;
    }

    /** {@code rootDir} 为空表示保持各文件当前目录（默认；此时为纯重命名）。 */
    public RenamePlan preview(String rootDir, List<RenameInput> inputs) {
        Path root = rootDir == null || rootDir.isBlank() ? null : Path.of(rootDir.trim());
        List<RenamePlanner.Source> sources = new ArrayList<>();
        if (inputs != null) {
            for (RenameInput input : inputs) {
                workspace.find(input.id())
                        .map(track -> new RenamePlanner.Source(track.id(), track.path(), input.trackNo(), input.title()))
                        .ifPresent(sources::add);
            }
        }
        return new RenamePlan(RenamePlanner.plan(sources, root));
    }
}
