package com.aiasti.tiger.application.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import com.aiasti.tiger.domain.model.ImageMimeTypes;
import com.aiasti.tiger.domain.model.TrackStatus;
import com.aiasti.tiger.domain.model.TrackTags;

/**
 * 工作区中的一个曲目（会话内状态，不跨会话持久化）。
 *
 * <p>按 DEV-002 维护「首次快照」「当前编辑值」与「最近成功落盘值」：首次快照在导入时建立且不再改变，
 * 当前编辑值在 S2 引入编辑能力后变化，最近落盘基线（{@link DiskBaseline}）在导入成功时记录、写回成功后更新。
 */
public final class Track {

    private final long id;
    private Path path;
    /** 首次读取快照：工作区生命周期内不变，作为提交前回滚目标（BIZ-006）。 */
    private final TrackTags firstSnapshot;
    private final long durationSeconds;
    /** 最近成功落盘值：导入成功时记录的文件指纹，写回成功后更新（DEV-002、DEV-003）。 */
    private DiskBaseline diskBaseline;
    /** 当前编辑值：S1 阶段与快照相同，S2 写回成功后更新为写入后的实际值。 */
    private TrackTags current;
    private TrackStatus status;

    private Track(long id, Path path, TrackTags firstSnapshot, long durationSeconds) {
        this.id = id;
        this.path = path;
        this.firstSnapshot = firstSnapshot;
        this.current = firstSnapshot;
        this.durationSeconds = durationSeconds;
        this.diskBaseline = DiskBaseline.read(path);
        this.status = TrackStatus.SAVED;
    }

    static Track imported(long id, Path path, TrackTags tags, long durationSeconds) {
        return new Track(id, path, tags, durationSeconds);
    }

    public long id() {
        return id;
    }

    public Path path() {
        return path;
    }

    /** 导入时读取的音频时长（秒）；在线歌词匹配用它参与置信度评分。 */
    public long durationSeconds() {
        return durationSeconds;
    }

    public TrackTags firstSnapshot() {
        return firstSnapshot;
    }

    public TrackTags current() {
        return current;
    }

    /** 最近成功落盘基线；写回预检用它比对文件是否被外部修改（DEV-003）。 */
    DiskBaseline diskBaseline() {
        return diskBaseline;
    }

    /**
     * 写回成功后更新「最近成功落盘值」与状态（DEV-002、DEV-003）。
     *
     * <p>{@code written} 为写回后从磁盘读回的实际标签值，{@code baseline} 为写入后的实际文件指纹；
     * 首次快照保持不变，差异基准随之切换为新的落盘值。
     */
    synchronized void markWritten(TrackTags written, DiskBaseline baseline) {
        this.current = written;
        this.diskBaseline = baseline;
        this.status = TrackStatus.SAVED;
    }

    /**
     * 写回成功且路径已变更（重命名/归档）时更新「最近成功落盘值」、当前路径与状态（DEV-003 结论 2）。
     */
    synchronized void markWrittenAt(Path newPath, TrackTags written, DiskBaseline baseline) {
        this.path = newPath;
        markWritten(written, baseline);
    }

    public TrackStatus status() {
        return status;
    }

    /** 内嵌封面数据（无封面时为空）。 */
    Optional<CoverData> cover() {
        if (!current.hasCover()) {
            return Optional.empty();
        }
        byte[] data = current.cover();
        return Optional.of(new CoverData(data, ImageMimeTypes.detect(data)));
    }

    TrackView toView() {
        return new TrackView(
                id,
                path.getFileName().toString(),
                extension(path),
                path.toString(),
                humanSize(path),
                formatDuration(durationSeconds),
                current.hasCover(),
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

    private static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
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
