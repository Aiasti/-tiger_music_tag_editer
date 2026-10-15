package com.aiasti.tiger.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aiasti.tiger.application.workspace.TrackView;
import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.application.workspace.WorkspaceView;

/** S1 验证：导入用例的递归发现、去重、失败隔离与首次快照（FR-1、BIZ-006、BIZ-014）。 */
class ImportWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    private final Workspace workspace = new Workspace();

    private ImportWorkspaceService serviceWith(StubTagAccessPort port) {
        return new ImportWorkspaceService(new AudioFileDiscovery(port), port, workspace);
    }

    @Test
    void importsRecursivelyAndIsolatesReadFailures() throws IOException {
        createFile(tempDir, "ok1.mp3");
        createFile(tempDir, "bad.mp3");
        createFile(tempDir, "readme.txt");
        createFile(tempDir.resolve("sub"), "ok2.mp3");

        WorkspaceView view = serviceWith(new StubTagAccessPort("mp3"))
                .importPaths(List.of(tempDir.toString()), true);

        assertEquals(2, view.tracks().size(), "两个可读音频进入工作区");
        assertEquals(1, view.skipped().size(), "损坏文件单独进入跳过项");
        assertEquals("bad.mp3", view.skipped().getFirst().filename());
        assertTrue(view.skipped().getFirst().reason().contains("读取失败"));
    }

    @Test
    void reimportDoesNotDuplicateNorResetSnapshot() throws IOException {
        createFile(tempDir, "ok1.mp3");
        createFile(tempDir, "ok2.mp3");

        ImportWorkspaceService service = serviceWith(new StubTagAccessPort("mp3"));
        WorkspaceView first = service.importPaths(List.of(tempDir.toString()), true);
        WorkspaceView second = service.importPaths(List.of(tempDir.toString()), true);

        assertEquals(2, first.tracks().size());
        assertEquals(2, second.tracks().size(), "重复导入不新增曲目");
        assertEquals(
                first.tracks().stream().map(TrackView::id).toList(),
                second.tracks().stream().map(TrackView::id).toList(),
                "重复导入不重置已有曲目");
    }

    @Test
    void importedTrackCarriesTagsAndMatchingInitialSnapshot() throws IOException {
        createFile(tempDir, "ok.mp3");

        WorkspaceView view = serviceWith(new StubTagAccessPort("mp3"))
                .importPaths(List.of(tempDir.toString()), true);

        TrackView track = view.tracks().getFirst();
        assertEquals("ok.mp3", track.filename());
        assertEquals("mp3", track.ext());
        assertEquals("saved", track.status());
        assertEquals("ok.mp3", track.tags().title());
        assertEquals(track.tags(), track.initialSnapshot(), "导入时首次快照应等于当前值");
    }

    @Test
    void importedTrackCarriesDurationAndCoverFlag() throws IOException {
        createFile(tempDir, "ok.mp3");

        TrackView track = serviceWith(new StubTagAccessPort("mp3"))
                .importPaths(List.of(tempDir.toString()), true)
                .tracks().getFirst();

        assertEquals("2:05", track.duration(), "125 秒应格式化为 2:05");
        assertTrue(track.hasCover(), "内嵌封面应被标记");
    }

    @Test
    void removeTakesTracksOutOfWorkspaceOnly() throws IOException {
        createFile(tempDir, "ok1.mp3");
        createFile(tempDir, "ok2.mp3");

        ImportWorkspaceService service = serviceWith(new StubTagAccessPort("mp3"));
        WorkspaceView imported = service.importPaths(List.of(tempDir.toString()), true);
        long firstId = imported.tracks().getFirst().id();

        WorkspaceView afterRemove = service.removeTracks(List.of(firstId));

        assertEquals(1, afterRemove.tracks().size(), "移除后仅剩一首");
        assertTrue(afterRemove.tracks().stream().noneMatch(track -> track.id() == firstId));
        assertTrue(Files.exists(tempDir.resolve("ok1.mp3")), "移出工作区不应删除磁盘文件");
    }

    /** 清空工作区应释放会话内状态（BIZ-017）。 */
    @Test
    void clearWorkspaceEmptiesSessionState() throws IOException {
        createFile(tempDir, "a.mp3");
        createFile(tempDir, "bad.mp3");

        ImportWorkspaceService service = serviceWith(new StubTagAccessPort("mp3"));
        service.importPaths(List.of(tempDir.toString()), true);

        WorkspaceView cleared = service.clearWorkspace();

        assertTrue(cleared.tracks().isEmpty(), "清空后不应保留曲目");
        assertTrue(cleared.skipped().isEmpty(), "清空后不应保留跳过项");
        assertTrue(Files.exists(tempDir.resolve("a.mp3")), "清空工作区不应删除磁盘文件");
    }

    @Test
    void emptyInputReturnsEmptyWorkspace() {
        WorkspaceView view = serviceWith(new StubTagAccessPort("mp3")).importPaths(List.of(), true);

        assertTrue(view.tracks().isEmpty());
        assertTrue(view.skipped().isEmpty());
    }

    private static void createFile(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), "x");
    }
}
