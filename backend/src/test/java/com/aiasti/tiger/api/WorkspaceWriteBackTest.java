package com.aiasti.tiger.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

/**
 * S2-2 验证：写回标签到磁盘、外部修改阻断与预检失败（FR-8、DEV-003、BIZ-028）。
 *
 * <p>工作区为会话内单例，导入类用例会改变其状态，故每个用例后重建上下文。
 */
@SpringBootTest(properties = "app.security.token=test-token")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceWriteBackTest {

    private static final Path SAMPLES = Path.of("src", "test", "resources", "samples");
    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagAccessPort tagAccessPort;

    /** 写回成功后：磁盘标签更新、视图状态转「已保存」、恢复材料被清理。 */
    @Test
    void commitWritesTagsAndUpdatesWorkspace(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("track.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        long id = importTrack(file);

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(id, "New Title", "New Artist", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("written"))
                .andExpect(jsonPath("$.outcomes[0].tags.title").value("New Title"))
                .andExpect(jsonPath("$.outcomes[0].tags.artist").value("New Artist"));

        TrackTags onDisk = tagAccessPort.read(file).tags();
        assertEquals("New Title", onDisk.value(TagField.TITLE).orElse(""), "标题应写入磁盘");
        assertEquals("New Artist", onDisk.value(TagField.ARTIST).orElse(""), "艺术家应写入磁盘");
        assertTrue(noBackupLeft(dir), "写回成功后应删除恢复材料");

        mockMvc.perform(get("/api/workspace").header("X-App-Token", TOKEN))
                .andExpect(jsonPath("$.tracks[0].status").value("saved"))
                .andExpect(jsonPath("$.tracks[0].tags.title").value("New Title"));
    }

    /** 文件被执行前被外部修改：阻止该文件（stale）且不覆盖外部改动（DEV-003 结论 1）。 */
    @Test
    void commitBlocksFileModifiedExternally(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("track.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        long id = importTrack(file);

        tagAccessPort.write(file, TrackTags.empty().withValue(TagField.TITLE, "External Title"));

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(id, "New Title", "New Artist", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("stale"));

        assertEquals("External Title", tagAccessPort.read(file).tags().value(TagField.TITLE).orElse(""),
                "外部修改不应被覆盖");
    }

    /** 文件不存在时预检不通过，标记未执行（blocked）（DEV-003 阶段 1）。 */
    @Test
    void commitBlocksWhenFileMissing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("track.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        long id = importTrack(file);
        Files.delete(file);

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(id, "New Title", "New Artist", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("blocked"));
    }

    /** 提交空封面（空串）表示移除封面，写回后磁盘不再含封面。 */
    @Test
    void commitRemovesCoverWhenRequested(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cover.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        byte[] cover = Files.readAllBytes(SAMPLES.resolve("cover.png"));
        tagAccessPort.write(file, TrackTags.empty().withCover(cover));
        long id = importTrack(file);

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(id, "", "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("written"))
                .andExpect(jsonPath("$.outcomes[0].hasCover").value(false));

        assertFalse(tagAccessPort.read(file).tags().hasCover(), "封面应已从磁盘移除");
    }

    /** 未提交封面变更（cover=null）时保持原封面：只改文本字段不应丢失内嵌封面。 */
    @Test
    void commitKeepsExistingCoverWhenUnchanged(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("keep-cover.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        byte[] cover = Files.readAllBytes(SAMPLES.resolve("cover.png"));
        tagAccessPort.write(file, TrackTags.empty().withCover(cover));
        long id = importTrack(file);

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(id, "New Title", "New Artist", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("written"))
                .andExpect(jsonPath("$.outcomes[0].hasCover").value(true));

        TrackTags onDisk = tagAccessPort.read(file).tags();
        assertEquals("New Title", onDisk.value(TagField.TITLE).orElse(""), "标题应写入磁盘");
        assertTrue(onDisk.hasCover(), "未提交封面变更时应保留原封面");
    }

    /** 携带目标路径时：标签写入后执行重命名/归档，文件落到新路径（BIZ-030）。 */
    @Test
    void commitRenamesFileWhenTargetPathProvided(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("old.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        long id = importTrack(file);
        Path target = dir.resolve("1 Hello.mp3");
        String escapedTarget = target.toAbsolutePath().toString().replace("\\", "\\\\");

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(id, "Hello", "Artist", null, escapedTarget)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("written"))
                .andExpect(jsonPath("$.outcomes[0].filePath").value(target.toString()));

        assertFalse(Files.exists(file), "原路径文件应已移走");
        assertTrue(Files.exists(target), "目标路径应生成文件");
        assertEquals("Hello", tagAccessPort.read(target).tags().value(TagField.TITLE).orElse(""),
                "重命名后标签应保留");
        assertTrue(noBackupLeft(dir), "写回成功后不应残留恢复材料或中转文件");
    }

    /** 批内互换（A↔B 文件名互调）应以两阶段完成且都不丢失。 */
    @Test
    void commitSwapsFileNamesWithinBatch(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("1 One.mp3");
        Path b = dir.resolve("2 Two.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), a);
        Files.copy(SAMPLES.resolve("sample.mp3"), b);
        List<Long> ids = importTracks(List.of(a, b));
        long idA = ids.get(0);
        long idB = ids.get(1);

        String targetA = dir.resolve("2 Two.mp3").toAbsolutePath().toString().replace("\\", "\\\\");
        String targetB = dir.resolve("1 One.mp3").toAbsolutePath().toString().replace("\\", "\\\\");
        String body = "{\"tracks\":["
                + "{\"id\":" + idA + ",\"tags\":{\"title\":\"Two\"},\"cover\":null,\"targetPath\":\"" + targetA + "\"},"
                + "{\"id\":" + idB + ",\"tags\":{\"title\":\"One\"},\"cover\":null,\"targetPath\":\"" + targetB + "\"}"
                + "]}";

        mockMvc.perform(post("/api/workspace/commit")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0].outcome").value("written"))
                .andExpect(jsonPath("$.outcomes[1].outcome").value("written"));

        // 互换后：原 a 的文件（标签 Two）落到 2 Two.mp3；原 b 的文件（标签 One）落到 1 One.mp3
        assertEquals("One", tagAccessPort.read(a).tags().value(TagField.TITLE).orElse(""), "1 One.mp3 存放标签 One 的文件");
        assertEquals("Two", tagAccessPort.read(b).tags().value(TagField.TITLE).orElse(""), "2 Two.mp3 存放标签 Two 的文件");
        assertTrue(noBackupLeft(dir), "批量互换后不应残留中间文件");
    }

    private long importTrack(Path file) throws Exception {
        return importTracks(List.of(file)).get(0);
    }

    private List<Long> importTracks(List<Path> files) throws Exception {
        StringBuilder paths = new StringBuilder();
        for (Path file : files) {
            if (paths.length() > 0) {
                paths.append(',');
            }
            paths.append('"').append(file.toAbsolutePath().toString().replace("\\", "\\\\")).append('"');
        }
        String body = mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[" + paths + "],\"recursive\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return allTrackIds(body);
    }

    /** 从响应 JSON 中取出全部曲目 id（按返回顺序）。 */
    private static List<Long> allTrackIds(String json) {
        List<Long> ids = new ArrayList<>();
        int index = json.indexOf("\"id\":");
        while (index >= 0) {
            int start = index + "\"id\":".length();
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            ids.add(Long.parseLong(json, start, end, 10));
            index = json.indexOf("\"id\":", end);
        }
        return ids;
    }

    /** 构造写回请求体；cover 为 null 表示保持现状、空串表示移除封面。 */
    private static String commitBody(long id, String title, String artist, String cover) {
        return commitBody(id, title, artist, cover, null);
    }

    /** 构造写回请求体；targetPath 为 null 表示不改路径，否则标签写入后执行重命名/归档。 */
    private static String commitBody(long id, String title, String artist, String cover, String targetPath) {
        String coverJson = cover == null ? "null" : "\"" + cover + "\"";
        String pathJson = targetPath == null ? "null" : "\"" + targetPath + "\"";
        return "{\"tracks\":[{\"id\":" + id
                + ",\"tags\":{\"title\":\"" + title + "\",\"artist\":\"" + artist + "\"}"
                + ",\"cover\":" + coverJson
                + ",\"targetPath\":" + pathJson + "}]}";
    }

    private static boolean noBackupLeft(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.noneMatch(path -> {
                String name = path.getFileName().toString();
                return name.contains(".tigertag-") || name.contains(".tigermove-");
            });
        }
    }
}
