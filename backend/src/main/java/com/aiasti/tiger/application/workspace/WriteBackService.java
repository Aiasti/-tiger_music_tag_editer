package com.aiasti.tiger.application.workspace;

import java.io.IOException;
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
 * 写回与失败恢复用例（DEV-003、BIZ-025、BIZ-028）。
 *
 * <p>对每个待提交曲目按固定顺序执行五阶段：预检 → 恢复材料 → 执行 → 判定与补偿 → 收尾；
 * 逐文件互不阻断，任一阶段失败只影响该文件。
 * S2-2 只写标签（12 个元数据字段与封面），不重命名、不移动（路径规划属 S3）。
 */
@Service
public class WriteBackService {

    /** 恢复材料后缀；固定前缀便于整批兜底清理识别（DEV-003 结论 4）。 */
    private static final String BACKUP_SUFFIX = ".tigertag-";

    private final TagAccessPort tagAccess;
    private final Workspace workspace;

    public WriteBackService(TagAccessPort tagAccess, Workspace workspace) {
        this.tagAccess = tagAccess;
        this.workspace = workspace;
    }

    /** 逐文件写回；返回与提交清单同序的结果。 */
    public WriteBackResult writeAll(List<WriteCommand> commands) {
        List<WriteOutcome> outcomes = new ArrayList<>();
        if (commands != null) {
            for (WriteCommand command : commands) {
                outcomes.add(writeOne(command));
            }
        }
        return new WriteBackResult(outcomes);
    }

    private WriteOutcome writeOne(WriteCommand command) {
        Optional<Track> found = workspace.find(command.id());
        if (found.isEmpty()) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "曲目已不在工作区", null);
        }
        Track track = found.get();
        Path file = track.path();

        // ---- 阶段 1：预检 ----
        if (command.tags() == null) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "提交内容缺少标签值", file.toString());
        }
        if (!Files.exists(file)) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "文件不存在", file.toString());
        }
        if (!Files.isWritable(file)) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "文件不可写", file.toString());
        }
        Path directory = file.getParent();
        if (directory == null || !Files.isWritable(directory)) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "文件所在目录不可写", file.toString());
        }

        CoverIntent cover;
        try {
            cover = resolveCover(command.cover(), track);
        } catch (IllegalArgumentException e) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, e.getMessage(), file.toString());
        }

        DiskBaseline actual = DiskBaseline.read(file);
        if (!actual.available()) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "无法读取文件状态", file.toString());
        }
        DiskBaseline recorded = track.diskBaseline();
        if (!recorded.sameAs(actual)) {
            // 时间戳/大小与基线不符：重新读取并比对标签，仍一致则视为仅时间戳变化（DEV-003 结论 1）
            TrackTags diskTags = readQuietly(file);
            if (diskTags == null) {
                return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "读取文件标签失败", file.toString());
            }
            if (!sameMetadata(diskTags, track.current())) {
                return WriteOutcome.failed(command.id(), WriteOutcomeStatus.STALE,
                        "文件已被外部修改（时间戳/大小或标签与最近落盘值不符）", null, file.toString());
            }
        }

        if (usableSpace(file) < actual.sizeBytes() * 3 / 2) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "磁盘可用空间不足", file.toString());
        }

        TrackTags target = toTags(command.tags(), cover.bytes());

        // ---- 阶段 2：恢复材料（同目录整文件备份，DEV-003 结论 4）----
        Path backup = file.resolveSibling("." + file.getFileName() + BACKUP_SUFFIX + UUID.randomUUID() + ".bak");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            return WriteOutcome.notExecuted(command.id(), WriteOutcomeStatus.BLOCKED, "无法创建恢复材料", file.toString());
        }

        // ---- 阶段 3：执行（标签写入 + 校验）----
        TrackTags written;
        try {
            tagAccess.write(file, target);
            written = readRequired(file);
            if (!sameAll(written, target)) {
                throw new IOException("写回校验不一致（字段或封面与写入值不符）");
            }
        } catch (RuntimeException | IOException e) {
            // ---- 阶段 4：判定与补偿 ----
            boolean restored = restore(file, backup);
            TrackTags afterFailure = readQuietly(file);
            deleteQuietly(backup);
            if (restored) {
                return WriteOutcome.failed(command.id(), WriteOutcomeStatus.FAILED_RESTORED,
                        "写回失败，已恢复为执行前状态", null, file.toString());
            }
            return WriteOutcome.failed(command.id(), WriteOutcomeStatus.FAILED_MANUAL,
                    "写回失败且未能恢复原状，需人工处理",
                    manualDetail(file, afterFailure), file.toString());
        }

        // ---- 阶段 5：收尾（成功）----
        track.markWritten(written, DiskBaseline.read(file));
        deleteQuietly(backup);
        return WriteOutcome.written(command.id(), TagValues.of(written), file.toString(), written.hasCover());
    }

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

    /** 把界面标签值映射为领域值对象（空串表示清除该字段，DEV-003）。 */
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
                .withCover(cover);
    }

    /** 12 个元数据字段是否一致（空值与缺字段等价）；用于外部修改比对。 */
    private static boolean sameMetadata(TrackTags a, TrackTags b) {
        for (TagField field : TagField.values()) {
            if (!a.value(field).orElse("").equals(b.value(field).orElse(""))) {
                return false;
            }
        }
        return true;
    }

    /** 元数据字段是否与写入目标一致，且封面有无与目标一致；用于写入后校验。 */
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

    /** 用恢复材料把文件还原为执行前状态；还原后按大小再次确认。 */
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
            // 无法探测可用空间时不阻塞写回
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

    /** 补偿失败时探测文件实际状态并给出建议动作（DEV-003 结论 5）。 */
    private static String manualDetail(Path file, TrackTags actual) {
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
        detail.append("；建议：人工核对文件后决定恢复、重试或移出工作区");
        return detail.toString();
    }

    private record CoverIntent(byte[] bytes) {
    }
}
