package com.aiasti.tiger.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private long importTrack(Path file) throws Exception {
        String body = mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(file)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(firstTrackId(body));
    }

    private static String importBody(Path path) {
        String escaped = path.toAbsolutePath().toString().replace("\\", "\\\\");
        return "{\"paths\":[\"" + escaped + "\"],\"recursive\":true}";
    }

    /** 构造写回请求体；cover 为 null 表示保持现状、空串表示移除封面。 */
    private static String commitBody(long id, String title, String artist, String cover) {
        String coverJson = cover == null ? "null" : "\"" + cover + "\"";
        return "{\"tracks\":[{\"id\":" + id
                + ",\"tags\":{\"title\":\"" + title + "\",\"artist\":\"" + artist + "\"}"
                + ",\"cover\":" + coverJson + "}]}";
    }

    private static boolean noBackupLeft(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.noneMatch(path -> path.getFileName().toString().contains(".tigertag-"));
        }
    }

    /** 从响应 JSON 中取出首个曲目 id（避免引入额外 JSON 解析依赖）。 */
    private static String firstTrackId(String json) {
        int index = json.indexOf("\"id\":");
        int start = index + "\"id\":".length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return json.substring(start, end);
    }
}
