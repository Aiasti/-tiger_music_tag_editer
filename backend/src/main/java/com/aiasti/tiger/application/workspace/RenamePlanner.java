package com.aiasti.tiger.application.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 重命名目标路径规划（FR-7、DEV-003 结论 3、BIZ-029）。
 *
 * <p>内置固定模板只产出文件名 {@code <音轨号> <标题>}（保留原扩展名），目录来自目标根目录（默认各文件所在目录）。
 * 负责非法字符清洗、保留设备名与长度处理、重名冲突自动加序号；<b>本类只做规划，不触碰磁盘</b>
 * （实际移动/重命名由 S3-2 执行）。
 */
public final class RenamePlanner {

    /** Windows 文件名非法字符。 */
    private static final String ILLEGAL_CHARS = "<>:\"/\\|?*";
    /** Windows 保留设备名（不区分大小写）。 */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    /** 单个路径段长度上限（DEV-003 结论 3）。 */
    private static final int MAX_SEGMENT_LENGTH = 120;
    /** 整路径长度上限（DEV-003 结论 3）。 */
    private static final int MAX_PATH_LENGTH = 240;
    /** 清洗后全空时的兜底文件名主体。 */
    private static final String FALLBACK_BASE = "未命名";

    private RenamePlanner() {
    }

    /** 规划输入：曲目 id、当前路径与命名所需字段。 */
    public record Source(long id, Path path, String trackNo, String title) {
    }

    /** 规划每个曲目的目标路径；{@code rootDir} 为 {@code null} 时保持各文件当前目录。 */
    public static List<RenamePlanItem> plan(List<Source> sources, Path rootDir) {
        Set<String> batchKeys = new HashSet<>();
        sources.forEach(source -> batchKeys.add(key(source.path())));

        List<Desired> desired = new ArrayList<>();
        for (Source source : sources) {
            String oldName = source.path().getFileName().toString();
            String extension = extensionOf(oldName);
            Path directory = rootDir != null ? rootDir : source.path().getParent();
            String base = baseName(source.trackNo(), source.title());
            String newName = base == null
                    ? oldName
                    : fitLength(directory, sanitizeSegment(base) + extension, extension);
            Path target = directory.resolve(newName);
            desired.add(new Desired(source, target, !samePath(target, source.path())));
        }

        // 不改名的曲目保留原路径，须先占位，避免其他曲目抢占（BIZ-029 仅改文件名，原地冲突更常见）
        Set<String> taken = new HashSet<>();
        desired.stream().filter(item -> !item.changed()).forEach(item -> taken.add(key(item.target())));

        List<RenamePlanItem> items = new ArrayList<>();
        for (Desired item : desired) {
            Path target = item.target();
            boolean adjusted = false;
            if (item.changed()) {
                int suffix = 2;
                while (occupied(target, taken, batchKeys)) {
                    target = withSequence(target, suffix++);
                    adjusted = true;
                }
                taken.add(key(target));
            }
            items.add(new RenamePlanItem(
                    item.source().id(),
                    item.source().path().toString(),
                    target.toString(),
                    item.source().path().getFileName().toString(),
                    target.getFileName().toString(),
                    item.changed(),
                    adjusted,
                    note(item, target, adjusted, batchKeys)));
        }
        return items;
    }

    private static String note(Desired item, Path target, boolean adjusted, Set<String> batchKeys) {
        if (adjusted) {
            return "因冲突自动调整";
        }
        if (item.changed() && batchKeys.contains(key(target)) && !samePath(target, item.source().path())) {
            return "批内顺序调整（执行时以中转名两步完成）";
        }
        return null;
    }

    /** 目标是否已被占用：本次已排定的目标，或磁盘上存在且不属于本批源文件的路径。 */
    private static boolean occupied(Path target, Set<String> taken, Set<String> batchKeys) {
        return taken.contains(key(target)) || (Files.exists(target) && !batchKeys.contains(key(target)));
    }

    /** 目标名是否与当前不同（Windows 路径不区分大小写）。 */
    private static boolean samePath(Path a, Path b) {
        return key(a).equals(key(b));
    }

    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }

    private static String baseName(String trackNo, String title) {
        String number = sanitizeSegment(trackNo);
        String name = sanitizeSegment(title);
        if (number.isEmpty() && name.isEmpty()) {
            return null;
        }
        return (number + " " + name).trim();
    }

    /** 清洗单个路径段：非法字符与控制字符替换为 {@code _}，去尾部空格与点，保留设备名加后缀。 */
    static String sanitizeSegment(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            cleaned.append(c < 0x20 || ILLEGAL_CHARS.indexOf(c) >= 0 ? '_' : c);
        }
        String result = stripTrailing(cleaned.toString());
        if (RESERVED_NAMES.contains(result.toUpperCase(Locale.ROOT))) {
            result = result + "_";
        }
        return result;
    }

    private static String stripTrailing(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '.')) {
            end--;
        }
        return value.substring(0, end);
    }

    /** 控制总长度：文件名段不超过 {@link #MAX_SEGMENT_LENGTH}、整路径不超过 {@link #MAX_PATH_LENGTH}。 */
    static String fitLength(Path directory, String fileName, String extension) {
        String result = fileName;
        String base = result.substring(0, result.length() - extension.length());
        int limit = MAX_SEGMENT_LENGTH;
        int dirLength = directory.toString().length() + 1;
        if (dirLength + limit > MAX_PATH_LENGTH) {
            limit = Math.max(1, MAX_PATH_LENGTH - dirLength);
        }
        if (base.length() > limit) {
            base = base.substring(0, limit);
        }
        if (base.isEmpty()) {
            base = FALLBACK_BASE;
        }
        return base + extension;
    }

    /** 在扩展名前追加序号：{@code name.mp3} → {@code name (2).mp3}。 */
    static Path withSequence(Path target, int sequence) {
        String fileName = target.getFileName().toString();
        String extension = extensionOf(fileName);
        String base = fileName.substring(0, fileName.length() - extension.length());
        return target.resolveSibling(base + " (" + sequence + ")" + extension);
    }

    private record Desired(Source source, Path target, boolean changed) {
    }
}
