package com.aiasti.tiger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** S1 验证：工作区接口与访问 token（FR-1、BIZ-013）。 */
@SpringBootTest(properties = "app.security.token=test-token")
@AutoConfigureMockMvc
class WorkspaceApiTest {

    @Autowired
    private MockMvc mockMvc;

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
        mockMvc.perform(get("/api/workspace").header("X-App-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isArray())
                .andExpect(jsonPath("$.tracks").isEmpty())
                .andExpect(jsonPath("$.skipped").isEmpty());
    }

    @Test
    void importWithEmptyPathsReturnsEmptyWorkspace() throws Exception {
        mockMvc.perform(post("/api/workspace/import")
                        .header("X-App-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[],\"recursive\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isEmpty())
                .andExpect(jsonPath("$.skipped").isEmpty());
    }
}
