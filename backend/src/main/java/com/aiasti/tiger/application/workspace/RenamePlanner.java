package com.aiasti.tiger.application.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 重命名 / 归档目标路径规划（FR-7、DEV-003 结论 3、BIZ-029、BIZ-030）。
 *
 * <p>重命名只改文件名（模板默认 {@code %track% %title%}）；归档移动文件位置（{@code keep} 不移动、
 * {@code flat} 平铺到所选目录、{@code template} 按目录模板建子目录）。负责模板渲染、非法字符清洗、
 * 保留设备名与长度处理、重名冲突自动加序号；<b>本类只做规划，不触碰磁盘</b>。
 */
public final class RenamePlanner {

    /** 支持的模板占位符（不含 {@code %}）。 */
    public static final List<String> PLACEHOLDERS =
            List.of("track", "title", "artist", "albumArtist", "album", "disc", "year");

    /** 重命名 / 归档模式。 */
    public enum Mode { RENAME, ARCHIVE }

    /** 归档位置：不移动 / 平铺到所选目录 / 按目录模板建子目录。 */
    public enum ArchiveMode { KEEP, FLAT, TEMPLATE }

    private static final String ILLEGAL_CHARS = "<>:\"/\\|?*";
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final int MAX_SEGMENT_LENGTH = 120;
    private static final int MAX_PATH_LENGTH = 240;
    private static final String FALLBACK_BASE = "未命名";

    private RenamePlanner() {
    }

    /** 规划输入：曲目 id、当前路径与模板所需字段值。 */
    public record Source(long id, Path path, Map<String, String> values) {
    }

    /** 规划每个曲目的目标路径；返回与输入同序的结果。 */
    public static List<RenamePlanItem> plan(List<Source> sources, Mode mode, String nameTemplate,
                                            String dirTemplate, Path rootDir, ArchiveMode archiveMode) {
        Set<String> batchKeys = new HashSet<>();
        sources.forEach(source -> batchKeys.add(key(source.path())));

        List<Desired> desired = new ArrayList<>();
        for (Source source : sources) {
            desired.add(desire(source, mode, nameTemplate, dirTemplate, rootDir, archiveMode));
        }

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

    private static Desired desire(Source source, Mode mode, String nameTemplate, String dirTemplate,
                                  Path rootDir, ArchiveMode archiveMode) {
        Path current = source.path();
        String oldName = current.getFileName().toString();
        if (mode == Mode.RENAME) {
            String rendered = render(nameTemplate, source.values());
            if (rendered.isBlank()) {
                return new Desired(source, current, false);
            }
            String extension = extensionOf(oldName);
            Path directory = current.getParent();
            String name = fitLength(directory, sanitizeSegment(rendered) + extension, extension);
            Path target = directory.resolve(name);
            return new Desired(source, target, !samePath(target, current));
        }
        // 归档：文件名不变
        if (archiveMode == null || archiveMode == ArchiveMode.KEEP) {
            return new Desired(source, current, false);
        }
        Path directory = rootDir;
        if (archiveMode == ArchiveMode.TEMPLATE) {
            directory = appendTemplate(rootDir, render(dirTemplate, source.values()));
        }
        Path target = fitArchive(directory, oldName);
        return new Desired(source, target, !samePath(target, current));
    }

    /** 把目录模板渲染结果按分隔符拆成多段并追加到根目录下（每段单独清洗，空段丢弃）。 */
    private static Path appendTemplate(Path root, String rendered) {
        Path result = root;
        for (String segment : rendered.split("[/\\\\]+")) {
            String cleaned = sanitizeSegment(segment);
            if (!cleaned.isEmpty()) {
                result = result.resolve(cleaned);
            }
        }
        return result;
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

    private static boolean occupied(Path target, Set<String> taken, Set<String> batchKeys) {
        return taken.contains(key(target)) || (Files.exists(target) && !batchKeys.contains(key(target)));
    }

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

    /** 渲染模板：替换已知占位符（{@code %name%}），未知占位符保持原样。 */
    static String render(String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (String placeholder : PLACEHOLDERS) {
            String value = values.getOrDefault(placeholder, "");
            result = result.replace("%" + placeholder + "%", value == null ? "" : value);
        }
        return result;
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
        String result = stripTrailing(cleaned.toString()).trim();
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

    /** 重命名：控制文件名段与整路径长度。 */
    static String fitLength(Path directory, String fileName, String extension) {
        String base = fileName.substring(0, fileName.length() - extension.length());
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

    /** 归档：文件名不变，若整路径超长则逐层丢弃最深的目录段。 */
    static Path fitArchive(Path directory, String fileName) {
        Path current = directory;
        while (current != null && current.toString().length() + 1 + fileName.length() > MAX_PATH_LENGTH) {
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return (current == null ? directory : current).resolve(fileName);
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
