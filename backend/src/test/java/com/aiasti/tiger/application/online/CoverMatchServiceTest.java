package com.aiasti.tiger.application.online;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.domain.model.CoverCandidate;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.CoverSearchPort;

/** 封面匹配仅使用 iTunes 候选（FR-6、DEV-004、BIZ-048）。 */
class CoverMatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsItunesCandidatesSortedByScoreAndLimited() throws Exception {
        Workspace workspace = workspaceWithTrack();
        List<CoverCandidate> candidates = new ArrayList<>();
        for (int score = 70; score <= 120; score += 10) {
            candidates.add(itunes("itunes-" + score, score));
        }
        CoverMatchService service = new CoverMatchService(new FakeCoverSearch(candidates), workspace);

        CoverMatchResult result = service.match(query());

        assertEquals(5, result.candidates().size());
        assertEquals("itunes-120", result.candidates().get(0).id());
        assertTrue(result.candidates().get(0).imageUrl().contains("mzstatic.com"));
    }

    @Test
    void fallsBackToTrackTagsWhenQueryFieldsAreBlank() throws Exception {
        Workspace workspace = workspaceWithTrack();
        FakeCoverSearch coverSearch = new FakeCoverSearch(List.of(itunes("itunes-1", 90)));
        CoverMatchService service = new CoverMatchService(coverSearch, workspace);

        CoverMatchResult result = service.match(
                new TrackMatchQuery(1L, "", "", "", "", "", "", "", ""));

        assertEquals("Song", coverSearch.lastTitle);
        assertEquals("Album", coverSearch.lastAlbum);
        assertEquals("Artist", coverSearch.lastArtist);
        assertEquals("Song", result.keyword());
    }

    @Test
    void warnsWhenItunesSearchFails() throws Exception {
        Workspace workspace = workspaceWithTrack();
        CoverMatchService service = new CoverMatchService(
                new FakeCoverSearch(List.of(), new IllegalStateException("network down")), workspace);

        CoverMatchResult result = service.match(query());

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("iTunes 封面检索失败")));
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("未找到可用封面候选")));
    }

    @Test
    void doesNotModifyLyrics() throws Exception {
        Workspace workspace = workspaceWithTrack();
        String lyrics = "[00:01.00]keep me";
        CoverMatchService service = new CoverMatchService(
                new FakeCoverSearch(List.of(itunes("itunes-1", 95))), workspace);

        service.match(query());

        assertEquals(lyrics, workspace.find(1L).orElseThrow().current()
                .value(TagField.LYRICS).orElse(""), "封面匹配不得改动歌词");
    }

    private Workspace workspaceWithTrack() throws Exception {
        Path file = tempDir.resolve("song.mp3");
        Files.writeString(file, "audio");
        Workspace workspace = new Workspace();
        TrackTags tags = TrackTags.empty()
                .withValue(TagField.TITLE, "Song")
                .withValue(TagField.ARTIST, "Artist")
                .withValue(TagField.ALBUM, "Album")
                .withValue(TagField.LYRICS, "[00:01.00]keep me");
        workspace.addImported(file, tags, 200L);
        return workspace;
    }

    private static TrackMatchQuery query() {
        return new TrackMatchQuery(1L, "Song", "Artist", "Album", "Album Artist", "1", "1", "2020", "");
    }

    private static CoverCandidate itunes(String id, int score) {
        return new CoverCandidate(id, "iTunes", score, "Album", "Artist",
                "Album", "2020", "", "", "https://is1-ssl.mzstatic.com/" + id + "/600x600bb.jpg", "iTunes");
    }

    private static final class FakeCoverSearch implements CoverSearchPort {
        private final List<CoverCandidate> candidates;
        private final RuntimeException failure;
        private String lastAlbum;
        private String lastArtist;
        private String lastTitle;

        private FakeCoverSearch(List<CoverCandidate> candidates) {
            this(candidates, null);
        }

        private FakeCoverSearch(List<CoverCandidate> candidates, RuntimeException failure) {
            this.candidates = candidates;
            this.failure = failure;
        }

        @Override
        public List<CoverCandidate> search(String album, String artist, String title) {
            this.lastAlbum = album;
            this.lastArtist = artist;
            this.lastTitle = title;
            if (failure != null) {
                throw failure;
            }
            return candidates;
        }
    }
}
