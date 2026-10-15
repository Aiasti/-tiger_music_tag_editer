package com.aiasti.tiger.api;

import java.nio.file.Files;
import java.nio.file.Path;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.TagAccessPort;

/**
 * S1 验证：工作区接口、访问 token、移出曲目与内嵌封面（FR-1、BIZ-013、BIZ-015）。
 *
 * <p>工作区为会话内单例，导入类用例会改变其状态，故每个用例后重建上下文。
 */
@SpringBootTest(properties = "app.security.token=test-token")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceApiTest {

    private static final Path SAMPLES = Path.of("src", "test", "resources", "samples");
    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagAccessPort tagAccessPort;

    @Test
    void healthEndpointIsOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void workspaceRequiresToken() throws Exception {
        mockMvc.perform(get("/api/workspace"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workspaceRejectsWrongToken() throws Exception {
        mockMvc.perform(get("/api/workspace").header("X-App-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workspaceReturnsEmptyViewWithValidToken() throws Exception {
        mockMvc.perform(get("/api/workspace").header("X-App-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isArray())
                .andExpect(jsonPath("$.tracks").isEmpty())
                .andExpect(jsonPath("$.skipped").isEmpty());
    }

    @Test
    void importWithEmptyPathsReturnsEmptyWorkspace() throws Exception {
        mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[],\"recursive\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isEmpty())
                .andExpect(jsonPath("$.skipped").isEmpty());
    }

    /** 桌面壳与本地服务不同源，CORS 预检必须放行且不要求 token（否则渲染层无法调用）。 */
    @Test
    void corsPreflightIsAllowedWithoutToken() throws Exception {
        mockMvc.perform(options("/api/workspace")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-App-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    /** 导入样本目录应返回 6 类格式的真实曲目，含时长。 */
    @Test
    void importSamplesReturnsTracksWithDuration() throws Exception {
        mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(SAMPLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(6))
                .andExpect(jsonPath("$.tracks[0].duration").exists())
                .andExpect(jsonPath("$.tracks[0].hasCover").exists());
    }

    /** 无内嵌封面时封面接口返回 404。 */
    @Test
    void coverEndpointReturnsNotFoundWhenTrackHasNoCover() throws Exception {
        String body = mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(SAMPLES)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String trackId = firstTrackId(body);

        mockMvc.perform(get("/api/workspace/tracks/" + trackId + "/cover").header("X-App-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    /** 内嵌封面存在时应返回图片二进制。 */
    @Test
    void coverEndpointReturnsImageWhenPresent(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("with-cover.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        byte[] cover = Files.readAllBytes(SAMPLES.resolve("cover.png"));
        tagAccessPort.write(file, TrackTags.empty().withCover(cover));

        String body = mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(file)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String trackId = firstTrackId(body);

        byte[] returned = mockMvc.perform(get("/api/workspace/tracks/" + trackId + "/cover")
                        .header("X-App-Token", TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertTrue(returned.length > 0, "应返回封面字节");
    }

    /** 移出工作区只改变会话状态，不删除磁盘文件（BIZ-015）。 */
    @Test
    void removeTracksKeepsFilesOnDisk() throws Exception {
        String body = mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(SAMPLES)))
                .andReturn().getResponse().getContentAsString();
        String trackId = firstTrackId(body);

        mockMvc.perform(post("/api/workspace/remove")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + trackId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(5));

        assertTrue(Files.exists(SAMPLES.resolve("sample-aac.m4a")), "移出工作区不应删除磁盘文件");
    }

    /** 清空接口应回到空工作区（BIZ-017：关闭重开必为空）。 */
    @Test
    void clearEmptiesWorkspace() throws Exception {
        mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(SAMPLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(6));

        mockMvc.perform(post("/api/workspace/clear").header("X-App-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isEmpty())
                .andExpect(jsonPath("$.skipped").isEmpty());
    }

    private static String importBody(Path path) {
        String escaped = path.toAbsolutePath().toString().replace("\\", "\\\\");
        return "{\"paths\":[\"" + escaped + "\"],\"recursive\":true}";
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
