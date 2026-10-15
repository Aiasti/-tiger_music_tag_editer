package com.aiasti.tiger.application.workspace;

import com.aiasti.tiger.domain.model.TrackStatus;
import com.aiasti.tiger.domain.model.TrackTags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 工作区中的一个曲目（会话内状态，不跨会话持久化）。
 *
 * <p>按 DEV-002 维护「首次快照」与「当前编辑值」；S1 只做导入，两者相同。
 * 「最近成功落盘值」在 S2 引入写回时补充。
 */
public final class Track {

    private final long id;
    private final Path path;
    /** 首次读取快照：工作区生命周期内不变，作为提交前回滚目标（BIZ-006）。 */
    private final TrackTags firstSnapshot;
    /** 当前编辑值：S1 阶段与快照相同，编辑能力在 S2 引入。 */
    private TrackTags current;
    private TrackStatus status;

    private Track(long id, Path path, TrackTags firstSnapshot) {
        this.id = id;
        this.path = path;
        this.firstSnapshot = firstSnapshot;
        this.current = firstSnapshot;
        this.status = TrackStatus.SAVED;
    }

    static Track imported(long id, Path path, TrackTags tags) {
        return new Track(id, path, tags);
    }

    public long id() {
        return id;
    }

    public Path path() {
        return path;
    }

    public TrackTags firstSnapshot() {
        return firstSnapshot;
    }

    public TrackTags current() {
        return current;
    }

    public TrackStatus status() {
        return status;
    }

    TrackView toView() {
        return new TrackView(
                id,
                path.getFileName().toString(),
                extension(path),
                path.toString(),
                humanSize(path),
                "",
                status.contractValue(),
                null,
                TagValues.of(current),
                TagValues.of(firstSnapshot));
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String humanSize(Path path) {
        try {
            return humanSize(Files.size(path));
        } catch (IOException e) {
            return "";
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
    }
}
