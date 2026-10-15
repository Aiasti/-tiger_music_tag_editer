package com.aiasti.tiger.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** S1 验证：文件发现遵循递归与扩展名过滤规则（FR-1、BIZ-014）。 */
class AudioFileDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversRecursivelyAndSilentlyIgnoresNonAudio() throws IOException {
        createFile(tempDir, "a.mp3");
        createFile(tempDir, "b.flac");
        createFile(tempDir, "note.txt");
        createFile(tempDir.resolve("sub"), "c.mp3");
        createFile(tempDir.resolve("sub").resolve("deep"), "d.ogg");

        AudioFileDiscovery discovery = new AudioFileDiscovery(new StubTagAccessPort("mp3", "flac", "ogg"));

        List<Path> recursive = discovery.discover(List.of(tempDir), true);
        assertEquals(4, recursive.size(), "应递归发现 4 个音频文件");
        assertTrue(recursive.stream().noneMatch(path -> path.toString().endsWith(".txt")),
                "非音频文件应被静默忽略");

        List<Path> shallow = discovery.discover(List.of(tempDir), false);
        assertEquals(2, shallow.size(), "非递归应只发现顶层 2 个音频文件");
    }

    @Test
    void ignoresUnsupportedFileSelectedDirectly() throws IOException {
        Path text = createFile(tempDir, "readme.txt");

        AudioFileDiscovery discovery = new AudioFileDiscovery(new StubTagAccessPort("mp3"));

        assertTrue(discovery.discover(List.of(text), true).isEmpty(),
                "直接选中非音频文件也应被忽略");
    }

    @Test
    void resultsAreSortedForStableOrder() throws IOException {
        createFile(tempDir, "z.mp3");
        createFile(tempDir, "a.mp3");
        createFile(tempDir, "m.mp3");

        AudioFileDiscovery discovery = new AudioFileDiscovery(new StubTagAccessPort("mp3"));

        List<Path> found = discovery.discover(List.of(tempDir), false);
        List<String> names = found.stream().map(path -> path.getFileName().toString()).toList();
        assertEquals(List.of("a.mp3", "m.mp3", "z.mp3"), names);
    }

    private static Path createFile(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), "x");
    }
}
