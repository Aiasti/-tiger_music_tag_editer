package com.aiasti.tiger.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.application.workspace.CoverData;
import com.aiasti.tiger.application.workspace.SkippedFile;
import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.application.workspace.WorkspaceView;
import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.AudioMetadata;
import com.aiasti.tiger.domain.port.TagAccessPort;

/**
 * 工作区用例（FR-1）：导入、查询与移除曲目。
 *
 * <p>导入时发现文件 → 去重 → 读取标签与时长 → 建立首次快照 → 返回工作区视图；
 * 单个文件读取失败按文件隔离，进入跳过项，不阻断整批导入。
 */
@Service
public class ImportWorkspaceService {

    private final AudioFileDiscovery discovery;
    private final TagAccessPort tagAccess;
    private final Workspace workspace;

    public ImportWorkspaceService(AudioFileDiscovery discovery,
                                  TagAccessPort tagAccess,
                                  Workspace workspace) {
        this.discovery = discovery;
        this.tagAccess = tagAccess;
        this.workspace = workspace;
    }

    public WorkspaceView importPaths(List<String> rawPaths, boolean recursive) {
        List<Path> candidates = discovery.discover(toPaths(rawPaths), recursive);
        List<SkippedFile> skipped = new ArrayList<>();
        for (Path file : candidates) {
            if (workspace.contains(file)) {
                continue;
            }
            try {
                AudioMetadata metadata = tagAccess.read(file);
                workspace.addImported(file, metadata.tags(), metadata.durationSeconds());
            } catch (TagAccessException e) {
                skipped.add(new SkippedFile(fileName(file), file.toString(), reasonOf(e)));
            }
        }
        workspace.addSkipped(skipped);
        return workspace.view();
    }

    /** 将指定曲目移出工作区（BIZ-015）：仅会话内移除，不改磁盘。 */
    public WorkspaceView removeTracks(List<Long> ids) {
        workspace.remove(ids);
        return workspace.view();
    }

    /** 清空工作区（BIZ-017）：供桌面壳在应用启动时调用，确保重启即空。 */
    public WorkspaceView clearWorkspace() {
        workspace.clear();
        return workspace.view();
    }

    /** 取曲目内嵌封面（无封面时为空）。 */
    public Optional<CoverData> cover(long id) {
        return workspace.cover(id);
    }

    public WorkspaceView currentView() {
        return workspace.view();
    }

    private static List<Path> toPaths(List<String> rawPaths) {
        if (rawPaths == null) {
            return List.of();
        }
        return rawPaths.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .toList();
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private static String reasonOf(TagAccessException e) {
        Throwable cause = e.getCause();
        if (cause == null) {
            return "读取失败";
        }
        return "读取失败（" + cause.getClass().getSimpleName() + "）";
    }
}
