package com.aiasti.tiger.application.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aiasti.tiger.domain.model.TrackTags;

/** S2-1 验证：导入成功即记录「最近成功落盘值」基线（DEV-002、DEV-003）。 */
class TrackDiskBaselineTest {

    @TempDir
    Path tempDir;

    @Test
    void importedTrackRecordsFileBaseline() throws IOException {
        Path file = tempDir.resolve("ok.mp3");
        Files.writeString(file, "0123456789");
        FileTime modified = Files.getLastModifiedTime(file);

        DiskBaseline baseline = Track.imported(1L, file, TrackTags.empty(), 0L).diskBaseline();

        assertTrue(baseline.available(), "基线应可用");
        assertEquals(10L, baseline.sizeBytes(), "基线应记录导入时的文件大小");
        assertEquals(modified.toMillis(), baseline.modifiedMillis(), "基线应记录导入时的修改时间");
    }

    @Test
    void baselineIsUnavailableWhenAttributesCannotBeRead() {
        DiskBaseline baseline = DiskBaseline.read(tempDir.resolve("missing.mp3"));

        assertFalse(baseline.available(), "属性读取失败时基线应不可用，供写回预检按不通过处理");
    }
}
