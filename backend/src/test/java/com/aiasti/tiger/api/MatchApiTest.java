package com.aiasti.tiger.api;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiasti.tiger.application.online.CoverMatchResult;
import com.aiasti.tiger.application.online.CoverMatchService;
import com.aiasti.tiger.application.online.LyricsMatchQuery;
import com.aiasti.tiger.application.online.LyricsMatchResult;
import com.aiasti.tiger.application.online.LyricsMatchService;
import com.aiasti.tiger.application.online.MetadataMatchResult;
import com.aiasti.tiger.application.online.OnlineMetadataService;
import com.aiasti.tiger.application.online.TrackMatchQuery;
import com.aiasti.tiger.domain.model.CoverImage;
import com.aiasti.tiger.domain.port.CoverArtPort;

/**
 * S4 验证：匹配接口的请求字段映射与参数校验（FR-5、FR-6、DEV-004）。
 *
 * <p>用 Mock 替换应用服务，避免测试访问网络；同时锁死前后端字段名，防止 BUG-007 类不一致复发。
 */
@SpringBootTest(properties = "app.security.token=test-token")
@AutoConfigureMockMvc
class MatchApiTest {

    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnlineMetadataService metadataService;

    @MockitoBean
    private LyricsMatchService lyricsService;

    @MockitoBean
    private CoverMatchService coverMatchService;

    @MockitoBean
    private CoverArtPort coverArtPort;

    @Test
    void metadataEndpointMapsAllRequestFields() throws Exception {
        when(metadataService.match(any())).thenReturn(
                new MetadataMatchResult(1L, "kw", List.of(), List.of("warning"), true, false));

        mockMvc.perform(post("/api/match/metadata")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackId": 1,
                                  "title": "Song",
                                  "artist": "Artist",
                                  "album": "Album",
                                  "albumArtist": "Album Artist",
                                  "trackNo": "3",
                                  "discNo": "2",
                                  "year": "2020",
                                  "keyword": "kw"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(1))
                .andExpect(jsonPath("$.warnings[0]").value("warning"));

        ArgumentCaptor<TrackMatchQuery> captor = ArgumentCaptor.forClass(TrackMatchQuery.class);
        verify(metadataService).match(captor.capture());
        TrackMatchQuery query = captor.getValue();
        assertEquals(1L, query.trackId());
        assertEquals("Song", query.title());
        assertEquals("Artist", query.artist());
        assertEquals("Album", query.album());
        assertEquals("Album Artist", query.albumArtist());
        assertEquals("3", query.trackNo());
        assertEquals("2", query.discNo());
        assertEquals("2020", query.year());
        assertEquals("kw", query.keyword());
    }

    @Test
    void lyricsEndpointMapsRequestFields() throws Exception {
        when(lyricsService.search(any())).thenReturn(
                new LyricsMatchResult(2L, "kw", List.of(), List.of()));

        mockMvc.perform(post("/api/match/lyrics")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackId": 2,
                                  "title": "Song",
                                  "artist": "Artist",
                                  "album": "Album",
                                  "keyword": "kw"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(2));

        ArgumentCaptor<LyricsMatchQuery> captor = ArgumentCaptor.forClass(LyricsMatchQuery.class);
        verify(lyricsService).search(captor.capture());
        LyricsMatchQuery query = captor.getValue();
        assertEquals(2L, query.trackId());
        assertEquals("Song", query.title());
        assertEquals("Artist", query.artist());
        assertEquals("Album", query.album());
        assertEquals("kw", query.keyword());
    }

    @Test
    void coverEndpointMapsRequestFields() throws Exception {
        when(coverMatchService.match(any())).thenReturn(
                new CoverMatchResult(3L, "kw", List.of(), List.of()));

        mockMvc.perform(post("/api/match/cover")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackId": 3,
                                  "title": "Song",
                                  "artist": "Artist",
                                  "album": "Album",
                                  "albumArtist": "Album Artist",
                                  "trackNo": "3",
                                  "discNo": "2",
                                  "year": "2020",
                                  "keyword": "kw"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(3));

        ArgumentCaptor<TrackMatchQuery> captor = ArgumentCaptor.forClass(TrackMatchQuery.class);
        verify(coverMatchService).match(captor.capture());
        TrackMatchQuery query = captor.getValue();
        assertEquals(3L, query.trackId());
        assertEquals("Album", query.album());
        assertEquals("Album Artist", query.albumArtist());
        assertEquals("kw", query.keyword());
    }

    @Test
    void coverImageReturnsBytes() throws Exception {
        when(coverArtPort.front("group", "release"))
                .thenReturn(Optional.of(new CoverImage(new byte[] { 1, 2, 3 }, "image/jpeg")));

        mockMvc.perform(get("/api/match/cover/image")
                        .header("X-App-Token", TOKEN)
                        .param("releaseGroupId", "group")
                        .param("releaseId", "release"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] { 1, 2, 3 }));
    }

    @Test
    void coverImageReturnsNotFoundWhenMissing() throws Exception {
        when(coverArtPort.front("group", "release")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/match/cover/image")
                        .header("X-App-Token", TOKEN)
                        .param("releaseGroupId", "group")
                        .param("releaseId", "release"))
                .andExpect(status().isNotFound());
    }

    @Test
    void metadataEndpointRejectsMissingTrackId() throws Exception {
        mockMvc.perform(post("/api/match/metadata")
                        .header("X-App-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Song\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metadataEndpointRequiresToken() throws Exception {
        mockMvc.perform(post("/api/match/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":1}"))
                .andExpect(status().isUnauthorized());
    }
}
