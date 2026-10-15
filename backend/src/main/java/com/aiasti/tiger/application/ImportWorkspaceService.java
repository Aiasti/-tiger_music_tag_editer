package com.aiasti.tiger.application;

import com.aiasti.tiger.application.workspace.SkippedFile;
import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.application.workspace.WorkspaceView;
import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 导入用例（FR-1）：发现文件 → 去重 → 读取标签 → 建立首次快照 → 返回工作区视图。
 *
 * <p>单个文件读取失败按文件隔离，进入跳过项，不阻断整批导入。
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
                TrackTags tags = tagAccess.read(file);
                workspace.addImported(file, tags);
            } catch (TagAccessException e) {
                skipped.add(new SkippedFile(fileName(file), file.toString(), reasonOf(e)));
            }
        }
        workspace.addSkipped(skipped);
        return workspace.view();
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
