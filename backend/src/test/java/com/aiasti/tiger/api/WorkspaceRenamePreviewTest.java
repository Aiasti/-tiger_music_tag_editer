package com.aiasti.tiger.api;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S3-1 验证：重命名预览接口（FR-7、BIZ-029）——按当前编辑值规划目标名且不改动磁盘。 */
@SpringBootTest(properties = "app.security.token=test-token")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceRenamePreviewTest {

    private static final Path SAMPLES = Path.of("src", "test", "resources", "samples");
    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void previewPlansTemplateNameWithoutTouchingDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("track.mp3");
        Files.copy(SAMPLES.resolve("sample.mp3"), file);
        long id = importTrack(file);

        mockMvc.perform(post("/api/workspace/rename-preview")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootDir\":null,\"tracks\":[{\"id\":" + id
                                + ",\"trackNo\":\"1\",\"title\":\"Hello\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].newName").value("1 Hello.mp3"))
                .andExpect(jsonPath("$.items[0].changed").value(true))
                .andExpect(jsonPath("$.items[0].oldName").value("track.mp3"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(file), "预览不应改动磁盘");
    }

    private long importTrack(Path file) throws Exception {
        String escaped = file.toAbsolutePath().toString().replace("\\", "\\\\");
        String body = mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"" + escaped + "\"],\"recursive\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int index = body.indexOf("\"id\":");
        int start = index + "\"id\":".length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }
}
