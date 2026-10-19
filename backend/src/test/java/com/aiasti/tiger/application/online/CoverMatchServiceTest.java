package com.aiasti.tiger.application.online;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.domain.model.CoverCandidate;
import com.aiasti.tiger.domain.model.MetadataCandidate;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.CoverSearchPort;
import com.aiasti.tiger.domain.port.MusicBrainzPort;

/** S4 验证：封面匹配复用元数据/指纹结果并按专辑名回退（FR-6、DEV-004）。 */
class CoverMatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsMetadataCandidatesWithReleaseIdsToCoverCandidates() throws Exception {
        Workspace workspace = workspaceWithTrack();
        OnlineMetadataService metadata = mock(OnlineMetadataService.class);
        when(metadata.match(any())).thenReturn(new MetadataMatchResult(
                1L, "Song", List.of(
                        candidate("r1", "MusicBrainz", 95, "Song", "Album", "rg1", "rel1"),
                        candidate("r2", "MusicBrainz", 90, "Song", "Album", "", "")),
                List.of(), true, true));
        CoverMatchService service = new CoverMatchService(metadata, new FakeMusicBrainz(List.of()), new FakeCoverSearch(List.of()), workspace);

        CoverMatchResult result = service.match(query());

        assertEquals(1, result.candidates().size(), "没有发行组/发行版 ID 的候选不应用于封面");
        assertEquals("rg1", result.candidates().get(0).releaseGroupId());
        assertEquals("Album", result.candidates().get(0).album());
        assertTrue(result.candidates().get(0).source().contains("Cover Art Archive"));
    }

    @Test
    void fallsBackToAlbumReleaseSearch() throws Exception {
        Workspace workspace = workspaceWithTrack();
        OnlineMetadataService metadata = mock(OnlineMetadataService.class);
        when(metadata.match(any())).thenReturn(new MetadataMatchResult(
                1L, "Song", List.of(), List.of("未找到匹配候选"), false, false));
        FakeMusicBrainz musicBrainz = new FakeMusicBrainz(List.of(
                candidate("release-rel1", "MusicBrainz", 88, "Album", "Album", "rg1", "rel1")));
        CoverMatchService service = new CoverMatchService(metadata, musicBrainz, new FakeCoverSearch(List.of()), workspace);

        CoverMatchResult result = service.match(query());

        assertEquals(1, result.candidates().size());
        assertEquals("rg1", result.candidates().get(0).releaseGroupId());
        assertEquals("Album", musicBrainz.lastAlbum);
        assertTrue(result.warnings().stream().noneMatch(message -> message.contains("未找到匹配候选")),
                "封面回退成功时不应保留录音检索的空结果告警");
    }

    @Test
    void warnsWhenNoCandidateHasCoverSource() throws Exception {
        Workspace workspace = workspaceWithTrack();
        OnlineMetadataService metadata = mock(OnlineMetadataService.class);
        when(metadata.match(any())).thenReturn(new MetadataMatchResult(
                1L, "Song", List.of(candidate("r1", "MusicBrainz", 90, "Song", "Album", "", "")),
                List.of(), false, false));
        CoverMatchService service = new CoverMatchService(metadata, new FakeMusicBrainz(List.of()), new FakeCoverSearch(List.of()), workspace);

        CoverMatchResult result = service.match(query());

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("未找到可用封面候选")));
    }

    @Test
    void doesNotModifyLyrics() throws Exception {
        Workspace workspace = workspaceWithTrack();
        String lyrics = "[00:01.00]keep me";
        OnlineMetadataService metadata = mock(OnlineMetadataService.class);
        when(metadata.match(any())).thenReturn(new MetadataMatchResult(
                1L, "Song", List.of(candidate("r1", "MusicBrainz", 95, "Song", "Album", "rg1", "rel1")),
                List.of(), true, true));
        CoverMatchService service = new CoverMatchService(metadata, new FakeMusicBrainz(List.of()), new FakeCoverSearch(List.of()), workspace);

        service.match(query());

        assertEquals(lyrics, workspace.find(1L).orElseThrow().current()
                .value(TagField.LYRICS).orElse(""), "封面匹配不得改动歌词");
    }

    @Test
    void putsItunesCandidateWithImageUrlFirst() throws Exception {
        Workspace workspace = workspaceWithTrack();
        OnlineMetadataService metadata = mock(OnlineMetadataService.class);
        when(metadata.match(any())).thenReturn(new MetadataMatchResult(
                1L, "Song", List.of(candidate("r1", "MusicBrainz", 95, "Song", "Album", "rg1", "rel1")),
                List.of(), true, true));
        CoverCandidate itunes = new CoverCandidate("itunes-1", "iTunes", 90, "Album", "Artist",
                "Album", "2020", "", "", "https://is1-ssl.mzstatic.com/a.jpg/600x600bb.jpg", "iTunes");
        CoverMatchService service = new CoverMatchService(metadata, new FakeMusicBrainz(List.of()),
                new FakeCoverSearch(List.of(itunes)), workspace);

        CoverMatchResult result = service.match(query());

        assertEquals(2, result.candidates().size());
        assertEquals("itunes-1", result.candidates().get(0).id(), "可直接下载的 iTunes 封面应排在前面");
        assertTrue(result.candidates().get(0).imageUrl().contains("mzstatic.com"));
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

    private static MetadataCandidate candidate(String id, String source, int score, String title,
                                               String album, String releaseGroupId, String releaseId) {
        return new MetadataCandidate(id, source, score, title, "Artist", album, "Album Artist",
                "1", "1", "2020", "", releaseId, releaseGroupId, "detail");
    }

    private static final class FakeCoverSearch implements CoverSearchPort {
        private final List<CoverCandidate> candidates;

        private FakeCoverSearch(List<CoverCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<CoverCandidate> search(String album, String artist, String title) {
            return candidates;
        }
    }

    private static final class FakeMusicBrainz implements MusicBrainzPort {
        private final List<MetadataCandidate> releases;
        private String lastAlbum;

        private FakeMusicBrainz(List<MetadataCandidate> releases) {
            this.releases = releases;
        }

        @Override
        public List<MetadataCandidate> search(String title, String artist, String album, String keyword) {
            return List.of();
        }

        @Override
        public List<MetadataCandidate> searchReleases(String album, String artist) {
            this.lastAlbum = album;
            return releases;
        }
    }
}
