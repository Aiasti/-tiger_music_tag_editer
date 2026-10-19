package com.aiasti.tiger.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiasti.tiger.infrastructure.online.OnlineProperties;

/**
 * S5 验证：运行期在线设置的读写契约（BIZ-035）。
 */
@SpringBootTest(properties = "app.security.token=test-token")
@AutoConfigureMockMvc
class SettingsApiTest {

    private static final String TOKEN = "test-token";
    private static final String UA = "TestAgent/1.0 (test@example.com)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnlineProperties properties;

    @BeforeEach
    void reset() {
        properties.getMusicbrainz().setUserAgent(UA);
        properties.getAcoustid().setApiKey("");
        properties.getFpcalc().setPath("fpcalc");
    }

    @Test
    void getOmitsApiKeyAndReportsConfigured() throws Exception {
        properties.getAcoustid().setApiKey("secret-key");

        mockMvc.perform(get("/api/settings/online").header("X-App-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.musicBrainzUserAgent").value(UA))
                .andExpect(jsonPath("$.acoustidKeyConfigured").value(true))
                .andExpect(jsonPath("$.fpcalcPath").value("fpcalc"))
                .andExpect(jsonPath("$.acoustidApiKey").doesNotExist());
    }

    @Test
    void updateAppliesUserAgentKeyAndFpcalcPath() throws Exception {
        mockMvc.perform(put("/api/settings/online")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "musicBrainzUserAgent": "NewAgent/2.0 (a@b.c)",
                                  "acoustidApiKey": "key-123",
                                  "fpcalcPath": "C:/tools/fpcalc.exe"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.musicBrainzUserAgent").value("NewAgent/2.0 (a@b.c)"))
                .andExpect(jsonPath("$.acoustidKeyConfigured").value(true))
                .andExpect(jsonPath("$.fpcalcPath").value("C:/tools/fpcalc.exe"));

        assertEquals("NewAgent/2.0 (a@b.c)", properties.getMusicbrainz().getUserAgent());
        assertEquals("key-123", properties.getAcoustid().getApiKey());
        assertEquals("C:/tools/fpcalc.exe", properties.getFpcalc().getPath());
    }

    @Test
    void nullFieldsKeepExistingValues() throws Exception {
        properties.getAcoustid().setApiKey("keep-key");

        mockMvc.perform(put("/api/settings/online")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fpcalcPath\":\"custom-fpcalc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.musicBrainzUserAgent").value(UA))
                .andExpect(jsonPath("$.acoustidKeyConfigured").value(true));

        assertEquals("keep-key", properties.getAcoustid().getApiKey());
        assertEquals("custom-fpcalc", properties.getFpcalc().getPath());
    }

    @Test
    void emptyKeyClearsStoredKey() throws Exception {
        properties.getAcoustid().setApiKey("remove-me");

        mockMvc.perform(put("/api/settings/online")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acoustidApiKey\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoustidKeyConfigured").value(false));

        assertTrue(properties.getAcoustid().getApiKey().isBlank());
    }

    @Test
    void blankUserAgentIsRejected() throws Exception {
        mockMvc.perform(put("/api/settings/online")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"musicBrainzUserAgent\":\"  \"}"))
                .andExpect(status().isBadRequest());

        assertEquals(UA, properties.getMusicbrainz().getUserAgent());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/settings/online"))
                .andExpect(status().isUnauthorized());
    }
}
