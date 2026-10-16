package com.aiasti.tiger.application.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.api.RenamePreviewRequest;

/**
 * 重命名/归档预览用例（FR-7、BIZ-030）：按渲染层当前编辑值规划目标路径，不触碰磁盘。
 *
 * <p>规划结果供渲染层生成「待写回路径态」；实际移动/重命名在「确认写回」时执行。
 */
@Service
public class RenamePreviewService {

    private final Workspace workspace;

    public RenamePreviewService(Workspace workspace) {
        this.workspace = workspace;
    }

    public RenamePlan preview(RenamePreviewRequest request) {
        RenamePlanner.Mode mode = "archive".equalsIgnoreCase(request.mode())
                ? RenamePlanner.Mode.ARCHIVE
                : RenamePlanner.Mode.RENAME;
        RenamePlanner.ArchiveMode archiveMode = parseArchiveMode(request.archiveMode());
        Path root = request.rootDir() == null || request.rootDir().isBlank() ? null : Path.of(request.rootDir().trim());

        List<RenamePlanner.Source> sources = new ArrayList<>();
        if (request.tracks() != null) {
            for (RenameInput input : request.tracks()) {
                workspace.find(input.id())
                        .map(track -> new RenamePlanner.Source(track.id(), track.path(), valuesOf(input)))
                        .ifPresent(sources::add);
            }
        }
        return new RenamePlan(RenamePlanner.plan(
                sources, mode, request.nameTemplate(), request.dirTemplate(), root, archiveMode));
    }

    private static RenamePlanner.ArchiveMode parseArchiveMode(String raw) {
        if (raw == null) {
            return RenamePlanner.ArchiveMode.KEEP;
        }
        return switch (raw.toLowerCase()) {
            case "flat" -> RenamePlanner.ArchiveMode.FLAT;
            case "template" -> RenamePlanner.ArchiveMode.TEMPLATE;
            default -> RenamePlanner.ArchiveMode.KEEP;
        };
    }

    private static Map<String, String> valuesOf(RenameInput input) {
        Map<String, String> values = new HashMap<>();
        values.put("track", input.trackNo());
        values.put("title", input.title());
        values.put("artist", input.artist());
        values.put("albumArtist", input.albumArtist());
        values.put("album", input.album());
        values.put("disc", input.discNo());
        values.put("year", input.year());
        return values;
    }
}
