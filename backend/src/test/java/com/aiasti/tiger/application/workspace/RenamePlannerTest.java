package com.aiasti.tiger.application.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** S3-1 验证：重命名/归档目标路径规划（模板、清洗、冲突、批内互换、长度、归档三种位置）（FR-7、BIZ-030）。 */
class RenamePlannerTest {

    private static final String NAME_TEMPLATE = "%track% %title%";
    private static final String DIR_TEMPLATE = "%albumArtist%/%album%";

    // ---- 重命名 ----

    @Test
    void rendersTrackNumberAndTitle(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        RenamePlanItem item = rename(List.of(source(1L, file, Map.of("track", "03", "title", "Blue Sky"))), dir)
                .get(0);

        assertEquals("03 Blue Sky.mp3", item.newName(), "模板应渲染为「音轨号 标题」并保留扩展名");
        assertTrue(item.changed());
        assertEquals(dir.resolve("03 Blue Sky.mp3").toString(), item.newPath());
    }

    @Test
    void cleansIllegalCharacters(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        String newName = rename(List.of(source(1L, file, Map.of("track", "01", "title", "A/B:C*?"))), dir)
                .get(0).newName();

        assertFalse(newName.matches(".*[<>:\"/\\\\|?*].*"), "非法字符应被替换：" + newName);
        assertEquals("01 A_B_C__.mp3", newName);
    }

    @Test
    void keepsNameWhenTemplateRendersBlank(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        RenamePlanItem item = rename(List.of(new RenamePlanner.Source(1L, file, Map.of())), dir).get(0);

        assertFalse(item.changed(), "模板渲染为空时保持原名");
        assertEquals("orig.mp3", item.newName());
    }

    @Test
    void appendsReservedNameSuffix(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        String newName = rename(List.of(source(1L, file, Map.of("title", "CON"))), dir).get(0).newName();

        assertEquals("CON_.mp3", newName, "保留设备名应加后缀");
    }

    @Test
    void resolvesDuplicateTargetsWithinBatch(@TempDir Path dir) throws IOException {
        Path a = Files.writeString(dir.resolve("a.mp3"), "x");
        Path b = Files.writeString(dir.resolve("b.mp3"), "x");

        List<RenamePlanItem> items = rename(List.of(
                source(1L, a, Map.of("track", "1", "title", "Same")),
                source(2L, b, Map.of("track", "1", "title", "Same"))), dir);

        assertEquals("1 Same.mp3", items.get(0).newName());
        assertFalse(items.get(0).adjusted());
        assertEquals("1 Same (2).mp3", items.get(1).newName(), "批内重名应自动加序号");
        assertTrue(items.get(1).adjusted());
        assertNotNull(items.get(1).note());
    }

    @Test
    void resolvesConflictWithExistingFile(@TempDir Path dir) throws IOException {
        Path existing = Files.writeString(dir.resolve("9 Other.mp3"), "y");
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        RenamePlanItem item = rename(
                List.of(source(1L, file, Map.of("track", "9", "title", "Other"))), dir).get(0);

        assertEquals("9 Other (2).mp3", item.newName(), "与磁盘既有文件冲突应加序号且不覆盖");
        assertTrue(Files.exists(existing), "规划不应改动磁盘");
    }

    @Test
    void marksInBatchSwapForTwoPhaseExecution(@TempDir Path dir) throws IOException {
        Path a = Files.writeString(dir.resolve("1 One.mp3"), "x");
        Path b = Files.writeString(dir.resolve("2 Two.mp3"), "y");

        List<RenamePlanItem> items = rename(List.of(
                source(1L, a, Map.of("track", "2", "title", "Two")),
                source(2L, b, Map.of("track", "1", "title", "One"))), dir);

        assertEquals("2 Two.mp3", items.get(0).newName());
        assertEquals("1 One.mp3", items.get(1).newName());
        assertFalse(items.get(0).adjusted(), "批内互换不算冲突");
        assertNotNull(items.get(0).note(), "批内互换应提示两阶段执行");
    }

    @Test
    void truncatesOverlongNames(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        String newName = rename(List.of(source(1L, file, Map.of("track", "1", "title", "x".repeat(300)))), dir)
                .get(0).newName();

        assertTrue(newName.length() <= 120 + ".mp3".length(), "文件名段应截断到上限内：" + newName.length());
    }

    // ---- 归档 ----

    @Test
    void archiveKeepLeavesPathUnchanged(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("song.mp3"), "x");

        RenamePlanItem item = archive(List.of(source(1L, file, Map.of("album", "A"))),
                dir, DIR_TEMPLATE, RenamePlanner.ArchiveMode.KEEP).get(0);

        assertFalse(item.changed());
        assertEquals(file.toString(), item.newPath());
    }

    @Test
    void archiveFlatMovesIntoRootWithoutSubdirectories(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("src")).resolve("song.mp3");
        Files.writeString(source, "x");
        Path root = Files.createDirectories(dir.resolve("library"));

        RenamePlanItem item = archive(List.of(source(1L, source, Map.of("album", "A"))),
                root, DIR_TEMPLATE, RenamePlanner.ArchiveMode.FLAT).get(0);

        assertEquals(root.resolve("song.mp3").toString(), item.newPath(), "平铺到所选目录、文件名不变");
        assertTrue(item.changed());
    }

    @Test
    void archiveTemplateBuildsTwoLevelDirectories(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("src")).resolve("song.mp3");
        Files.writeString(source, "x");
        Path root = Files.createDirectories(dir.resolve("library"));

        RenamePlanItem item = archive(
                List.of(source(1L, source, Map.of("albumArtist", "AA", "album", "Album X"))),
                root, DIR_TEMPLATE, RenamePlanner.ArchiveMode.TEMPLATE).get(0);

        assertEquals(root.resolve("AA").resolve("Album X").resolve("song.mp3").toString(), item.newPath());
    }

    @Test
    void archiveSingleLevelTemplateCreatesOneDirectory(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("src")).resolve("song.mp3");
        Files.writeString(source, "x");
        Path root = Files.createDirectories(dir.resolve("library"));

        RenamePlanItem item = archive(
                List.of(source(1L, source, Map.of("albumArtist", "AA", "album", "Album X"))),
                root, "%album%", RenamePlanner.ArchiveMode.TEMPLATE).get(0);

        assertEquals(root.resolve("Album X").resolve("song.mp3").toString(), item.newPath(), "单层模板只建一层目录");
    }

    private static RenamePlanner.Source source(long id, Path path, Map<String, String> values) {
        return new RenamePlanner.Source(id, path, values);
    }

    private static List<RenamePlanItem> rename(List<RenamePlanner.Source> sources, Path dir) {
        return RenamePlanner.plan(sources, RenamePlanner.Mode.RENAME, NAME_TEMPLATE, null, dir, null);
    }

    private static List<RenamePlanItem> archive(List<RenamePlanner.Source> sources, Path rootDir,
                                                String dirTemplate, RenamePlanner.ArchiveMode mode) {
        return RenamePlanner.plan(sources, RenamePlanner.Mode.ARCHIVE, null, dirTemplate, rootDir, mode);
    }
}
