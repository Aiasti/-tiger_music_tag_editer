package com.aiasti.tiger.application.workspace;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.domain.exception.TagAccessException;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

/**
 * 写回与失败恢复用例（DEV-003、BIZ-025、BIZ-028、BIZ-030）。
 *
 * <p>三阶段：① 逐文件「预检 → 恢复材料 → 标签写入与校验 → 失败补偿」；② 路径变更（重命名/归档）两阶段执行
 * （先到同目录中转名，再到目标；同卷 {@code ATOMIC_MOVE}、跨卷复制后删源）；③ 收尾（更新落盘基线与当前路径、
 * 清理材料）。逐文件互不阻断；标签已写但路径变更失败 → {@code partial}（保持文件原路径）。
 */
@Service
public class WriteBackService {

    /** 恢复材料后缀；固定前缀便于识别（DEV-003 结论 4）。 */
    private static final String BACKUP_SUFFIX = ".tigertag-";
    /** 路径变更中转名后缀：批内互换/重名时先把源移到中转名，再落到目标。 */
    private static final String MOVETEMP_SUFFIX = ".tigermove-";

    private final TagAccessPort tagAccess;
    private final Workspace workspace;

    public WriteBackService(TagAccessPort tagAccess, Workspace workspace) {
        this.tagAccess = tagAccess;
        this.workspace = workspace;
    }

    /** 逐文件写回；返回与提交清单同序的结果。 */
    public WriteBackResult writeAll(List<WriteCommand> commands) {
        List<Job> jobs = new ArrayList<>();
        if (commands != null) {
            for (WriteCommand command : commands) {
                jobs.add(prepare(command));
            }
        }
        jobs.stream().filter(Job::hasTrack).forEach(this::writeTags);
        movePaths(jobs);
        List<WriteOutcome> outcomes = jobs.stream().map(this::finish).toList();
        workspace.reindex();
        return new WriteBackResult(outcomes);
    }

    private Job prepare(WriteCommand command) {
        Optional<Track> found = workspace.find(command.id());
        return found.map(track -> new Job(command, track))
                .orElseGet(() -> Job.notFound(command.id()));
    }

    // ---- 阶段 ①：预检 + 恢复材料 + 标签写入与校验 ----

    private void writeTags(Job job) {
        Track track = job.track;
        Path file = track.path();

        if (job.command.tags() == null) {
            job.block("提交内容缺少标签值");
            return;
        }
        if (!Files.exists(file)) {
            job.block("文件不存在");
            return;
        }
        if (!Files.isWritable(file)) {
            job.block("文件不可写");
            return;
        }
        Path directory = file.getParent();
        if (directory == null || !Files.isWritable(directory)) {
            job.block("文件所在目录不可写");
            return;
        }

        CoverIntent cover;
        try {
            cover = resolveCover(job.command.cover(), track);
        } catch (IllegalArgumentException e) {
            job.block(e.getMessage());
            return;
        }

        DiskBaseline actual = DiskBaseline.read(file);
        if (!actual.available()) {
            job.block("无法读取文件状态");
            return;
        }
        DiskBaseline recorded = track.diskBaseline();
        if (!recorded.sameAs(actual)) {
            TrackTags diskTags = readQuietly(file);
            if (diskTags == null) {
                job.block("读取文件标签失败");
                return;
            }
            if (!sameMetadata(diskTags, track.current())) {
                job.stale("文件已被外部修改（时间戳/大小或标签与最近落盘值不符）");
                return;
            }
        }

        if (usableSpace(file) < actual.sizeBytes() * 3 / 2) {
            job.block("磁盘可用空间不足");
            return;
        }

        job.targetPath = resolveTarget(job.command.targetPath(), file);
        TrackTags target = toTags(job.command.tags(), cover.bytes());

        Path backup = file.resolveSibling("." + file.getFileName() + BACKUP_SUFFIX + UUID.randomUUID() + ".bak");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            job.block("无法创建恢复材料");
            return;
        }
        job.backup = backup;

        try {
            tagAccess.write(file, target);
            TrackTags written = readRequired(file);
            if (!sameAll(written, target)) {
                throw new IOException("写回校验不一致（字段或封面与写入值不符）");
            }
            job.written = written;
            job.status = WriteOutcomeStatus.WRITTEN;
        } catch (RuntimeException | IOException e) {
            boolean restored = restore(file, backup);
            if (restored) {
                job.fail(WriteOutcomeStatus.FAILED_RESTORED, "写回失败，已恢复为执行前状态");
            } else {
                // 未恢复：保留恢复材料供人工处理，说明中给出其路径（DEV-003 结论 5）
                job.failManual("写回失败且未能恢复原状，需人工处理", manualDetail(file, readQuietly(file), backup));
            }
        }
    }

    // ---- 阶段 ②：路径变更（两阶段）----

    private void movePaths(List<Job> jobs) {
        List<Job> moving = jobs.stream()
                .filter(job -> job.status == WriteOutcomeStatus.WRITTEN
                        && job.targetPath != null
                        && !samePath(job.targetPath, job.track.path()))
                .toList();
        if (moving.isEmpty()) {
            return;
        }
        // 阶段 A：全部先移到同目录中转名，腾空原有路径，避免批内互换/同名互相覆盖。
        for (Job job : moving) {
            Path file = job.track.path();
            Path temp = file.resolveSibling("." + file.getFileName() + MOVETEMP_SUFFIX + UUID.randomUUID());
            try {
                Files.move(file, temp, StandardCopyOption.REPLACE_EXISTING);
                job.tempPath = temp;
            } catch (IOException e) {
                job.partial("路径变更失败（无法进入中转），标签已写");
            }
        }
        // 阶段 B：中转名 → 目标路径。
        for (Job job : moving) {
            if (job.tempPath == null) {
                continue;
            }
            Path target = job.targetPath;
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (Files.exists(target)) {
                    throw new IOException("目标已存在，可能被外部占用");
                }
                moveAcross(job.tempPath, target);
                TrackTags written = readQuietly(target);
                if (written == null) {
                    throw new IOException("移动后无法读取目标文件");
                }
                job.written = written;
                job.movedTo = target;
                job.tempPath = null;
            } catch (IOException | RuntimeException e) {
                boolean backToOriginal = moveBack(job);
                if (backToOriginal) {
                    job.partial("路径变更失败，文件已回到原路径；标签已写（原因：" + reasonOf(e) + "）");
                } else {
                    job.failManual("路径变更失败且未能回到原路径，需人工处理",
                            moveDetail(job, e));
                }
            }
        }
    }

    /** 同卷优先原子移动；跨卷退化为「复制 → 校验大小 → 删源」。 */
    private static void moveAcross(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(target) != Files.size(source)) {
                Files.deleteIfExists(target);
                throw new IOException("跨卷复制校验大小不一致");
            }
            Files.delete(source);
        }
    }

    /** 把中转文件移回原路径。 */
    private static boolean moveBack(Job job) {
        if (job.tempPath == null) {
            return true;
        }
        try {
            Files.move(job.tempPath, job.track.path(), StandardCopyOption.REPLACE_EXISTING);
            job.tempPath = null;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String moveDetail(Job job, Exception cause) {
        StringBuilder detail = new StringBuilder();
        detail.append("中转文件：").append(job.tempPath == null ? "（已丢失引用）" : job.tempPath);
        detail.append("，目标路径：").append(job.targetPath);
        detail.append("，原路径：").append(job.track.path());
        detail.append("；原因：").append(reasonOf(cause));
        detail.append("；建议：按上述路径人工核对后移回或移出工作区");
        return detail.toString();
    }

    // ---- 阶段 ③：收尾 ----

    private WriteOutcome finish(Job job) {
        if (job.track == null) {
            return WriteOutcome.notExecuted(job.id, WriteOutcomeStatus.BLOCKED, job.reason, null);
        }
        String currentPath = job.track.path().toString();
        return switch (job.status) {
            case WRITTEN -> {
                deleteQuietly(job.backup);
                String path = currentPath;
                if (job.movedTo != null) {
                    job.track.markWrittenAt(job.movedTo, job.written, DiskBaseline.read(job.movedTo));
                    path = job.movedTo.toString();
                } else {
                    job.track.markWritten(job.written, DiskBaseline.read(job.track.path()));
                }
                yield WriteOutcome.written(job.id, TagValues.of(job.written), path, job.written.hasCover());
            }
            case PARTIAL -> {
                deleteQuietly(job.backup);
                job.track.markWritten(job.written, DiskBaseline.read(job.track.path()));
                yield WriteOutcome.partial(job.id, TagValues.of(job.written), job.reason,
                        job.track.path().toString(), job.written.hasCover());
            }
            case STALE, BLOCKED -> WriteOutcome.notExecuted(job.id, job.status, job.reason, currentPath);
            default -> {
                // failed-manual 保留恢复材料与中转文件供人工处理，并在说明中给出路径
                if (job.status == WriteOutcomeStatus.FAILED_RESTORED) {
                    deleteQuietly(job.backup);
                }
                yield WriteOutcome.failed(job.id, job.status, job.reason, job.detail, currentPath);
            }
        };
    }

    // ---- 辅助 ----

    /** 封面意图：null=保持现状、空串=移除、data URL=替换。 */
    private CoverIntent resolveCover(String cover, Track track) {
        if (cover == null) {
            return new CoverIntent(track.current().cover());
        }
        if (cover.isEmpty()) {
            return new CoverIntent(null);
        }
        int comma = cover.indexOf(',');
        if (!cover.startsWith("data:") || comma < 0) {
            throw new IllegalArgumentException("封面数据格式无法识别");
        }
        try {
            return new CoverIntent(Base64.getDecoder().decode(cover.substring(comma + 1)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("封面数据无法解码");
        }
    }

    private static Path resolveTarget(String targetPath, Path file) {
        if (targetPath == null || targetPath.isBlank()) {
            return null;
        }
        Path target = Path.of(targetPath.trim());
        return samePath(target, file) ? null : target;
    }

    private static boolean samePath(Path a, Path b) {
        return a.toAbsolutePath().normalize().toString()
                .equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
    }

    /** 把界面标签值映射为领域值对象（空串表示清除该字段）。 */
    private static TrackTags toTags(TagValues values, byte[] cover) {
        return TrackTags.empty()
                .withValue(TagField.TITLE, values.title())
                .withValue(TagField.ARTIST, values.artist())
                .withValue(TagField.ALBUM, values.album())
                .withValue(TagField.ALBUM_ARTIST, values.albumArtist())
                .withValue(TagField.TRACK, values.trackNo())
                .withValue(TagField.DISC_NO, values.discNo())
                .withValue(TagField.YEAR, values.year())
                .withValue(TagField.GENRE, values.genre())
                .withValue(TagField.COMPOSER, values.composer())
                .withValue(TagField.LYRICIST, values.lyricist())
                .withValue(TagField.ARRANGER, values.arranger())
                .withValue(TagField.COMMENT, values.comment())
                .withValue(TagField.LYRICS, values.lyrics())
                .withCover(cover);
    }

    private static boolean sameMetadata(TrackTags a, TrackTags b) {
        for (TagField field : TagField.values()) {
            if (!a.value(field).orElse("").equals(b.value(field).orElse(""))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameAll(TrackTags written, TrackTags target) {
        return sameMetadata(written, target) && written.hasCover() == target.hasCover();
    }

    private TrackTags readRequired(Path file) {
        return tagAccess.read(file).tags();
    }

    private TrackTags readQuietly(Path file) {
        try {
            return tagAccess.read(file).tags();
        } catch (TagAccessException e) {
            return null;
        }
    }

    private static boolean restore(Path file, Path backup) {
        if (!Files.exists(backup)) {
            return false;
        }
        try {
            Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(file) == Files.size(backup);
        } catch (IOException e) {
            return false;
        }
    }

    private static long usableSpace(Path file) {
        try {
            return Files.getFileStore(file).getUsableSpace();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 残留路径供人工清理；此处不阻断整批
        }
    }

    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String manualDetail(Path file, TrackTags actual, Path backup) {
        StringBuilder detail = new StringBuilder();
        detail.append(Files.exists(file) ? "文件仍存在" : "文件已丢失");
        DiskBaseline baseline = DiskBaseline.read(file);
        if (baseline.available()) {
            detail.append("，大小 ").append(baseline.sizeBytes()).append(" 字节");
        }
        if (actual != null) {
            detail.append("，当前标题「").append(actual.value(TagField.TITLE).orElse(""))
                    .append("」艺术家「").append(actual.value(TagField.ARTIST).orElse("")).append("」");
        }
        if (backup != null && Files.exists(backup)) {
            detail.append("，恢复材料保留在 ").append(backup);
        }
        detail.append("；建议：请核对文件后决定恢复、重试、查看文件占用或移出工作区");
        return detail.toString();
    }

    private record CoverIntent(byte[] bytes) {
    }

    /** 单文件执行上下文。 */
    private static final class Job {
        private final long id;
        private final WriteCommand command;
        private final Track track;
        private WriteOutcomeStatus status = WriteOutcomeStatus.BLOCKED;
        private String reason;
        private String detail;
        private TrackTags written;
        private Path targetPath;
        private Path movedTo;
        private Path backup;
        private Path tempPath;

        private Job(WriteCommand command, Track track) {
            this.command = command;
            this.track = track;
            this.id = track.id();
        }

        private static Job notFound(long id) {
            Job job = new Job(id);
            job.block("曲目已不在工作区");
            return job;
        }

        private Job(long id) {
            this.id = id;
            this.command = null;
            this.track = null;
        }

        private boolean hasTrack() {
            return track != null;
        }

        private void block(String reason) {
            this.status = WriteOutcomeStatus.BLOCKED;
            this.reason = reason;
        }

        private void stale(String reason) {
            this.status = WriteOutcomeStatus.STALE;
            this.reason = reason;
        }

        private void fail(WriteOutcomeStatus status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        private void failManual(String reason, String detail) {
            this.status = WriteOutcomeStatus.FAILED_MANUAL;
            this.reason = reason;
            this.detail = detail;
        }

        private void partial(String reason) {
            this.status = WriteOutcomeStatus.PARTIAL;
            this.reason = reason;
        }
    }
}
