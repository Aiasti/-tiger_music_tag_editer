package com.aiasti.tiger.application.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** S3-1 验证：重命名目标路径规划（非法字符、缺字段、重名冲突、批内互换、长度）（FR-7、BIZ-029）。 */
class RenamePlannerTest {

    @Test
    void plansTrackNumberAndTitle(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        List<RenamePlanItem> items = RenamePlanner.plan(
                List.of(new RenamePlanner.Source(1L, file, "03", "Blue Sky")), dir);

        RenamePlanItem item = items.get(0);
        assertEquals("03 Blue Sky.mp3", item.newName(), "模板应为「音轨号 标题」并保留扩展名");
        assertTrue(item.changed(), "目标与当前不同");
        assertEquals(file.toString(), item.oldPath());
        assertEquals(dir.resolve("03 Blue Sky.mp3").toString(), item.newPath());
    }

    @Test
    void cleansIllegalCharacters(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        List<RenamePlanItem> items = RenamePlanner.plan(
                List.of(new RenamePlanner.Source(1L, file, "01", "A/B:C*?")), dir);

        String newName = items.get(0).newName();
        assertFalse(newName.matches(".*[<>:\"/\\\\|?*].*"), "非法字符应被替换：" + newName);
        assertEquals("01 A_B_C__.mp3", newName);
    }

    @Test
    void keepsNameWhenTemplateFieldsAreEmpty(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        List<RenamePlanItem> items = RenamePlanner.plan(
                List.of(new RenamePlanner.Source(1L, file, "", "")), dir);

        assertFalse(items.get(0).changed(), "无音轨号与标题时保持原名");
        assertEquals("orig.mp3", items.get(0).newName());
    }

    @Test
    void appendsReservedNameSuffix(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        List<RenamePlanItem> items = RenamePlanner.plan(
                List.of(new RenamePlanner.Source(1L, file, "", "CON")), dir);

        assertEquals("CON_.mp3", items.get(0).newName(), "保留设备名应加后缀");
    }

    @Test
    void resolvesDuplicateTargetsWithinBatch(@TempDir Path dir) throws IOException {
        Path a = Files.writeString(dir.resolve("a.mp3"), "x");
        Path b = Files.writeString(dir.resolve("b.mp3"), "x");

        List<RenamePlanItem> items = RenamePlanner.plan(List.of(
                new RenamePlanner.Source(1L, a, "1", "Same"),
                new RenamePlanner.Source(2L, b, "1", "Same")), dir);

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

        List<RenamePlanItem> items = RenamePlanner.plan(
                List.of(new RenamePlanner.Source(1L, file, "9", "Other")), dir);

        assertEquals("9 Other (2).mp3", items.get(0).newName(), "与磁盘既有文件冲突应加序号且不覆盖");
        assertTrue(Files.exists(existing), "规划不应改动磁盘");
    }

    @Test
    void marksInBatchSwapForTwoPhaseExecution(@TempDir Path dir) throws IOException {
        Path a = Files.writeString(dir.resolve("1 One.mp3"), "x");
        Path b = Files.writeString(dir.resolve("2 Two.mp3"), "y");

        List<RenamePlanItem> items = RenamePlanner.plan(List.of(
                new RenamePlanner.Source(1L, a, "2", "Two"),
                new RenamePlanner.Source(2L, b, "1", "One")), dir);

        assertEquals("2 Two.mp3", items.get(0).newName());
        assertEquals("1 One.mp3", items.get(1).newName());
        assertFalse(items.get(0).adjusted(), "批内互换不算冲突");
        assertNotNull(items.get(0).note(), "批内互换应提示两阶段执行");
    }

    @Test
    void truncatesOverlongNames(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("orig.mp3"), "x");

        List<RenamePlanItem> items = RenamePlanner.plan(
                List.of(new RenamePlanner.Source(1L, file, "1", "x".repeat(300))), dir);

        String newName = items.get(0).newName();
        assertTrue(newName.length() <= 120 + ".mp3".length(), "文件名段应截断到上限内：" + newName.length());
    }
}
