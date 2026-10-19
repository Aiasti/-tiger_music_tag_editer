package com.aiasti.tiger.application.online;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;
import com.aiasti.tiger.domain.model.MetadataCandidate;
import com.aiasti.tiger.domain.model.TrackTags;
import com.aiasti.tiger.domain.port.AcoustIdPort;
import com.aiasti.tiger.domain.port.LyricsPort;
import com.aiasti.tiger.domain.port.MusicBrainzPort;

/** S4 验证：元数据/歌词匹配的合并、文件名回退与失败降级（FR-5、FR-6、DEV-004）。 */
class OnlineMatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesMusicBrainzAndAcoustIdByRecordingId() throws Exception {
        Workspace workspace = workspaceWithTrack("01 Tiger Song.mp3", tags("Tiger Song", "Tiger Band", "Tiger Album"));
        FakeMusicBrainz musicBrainz = new FakeMusicBrainz(List.of(candidate("r1", "MusicBrainz", 90, "Tiger Song", "Tiger Album")));
        FakeAcoustId acoustId = new FakeAcoustId(true, "", List.of(candidate("r1", "AcoustID", 97, "Tiger Song", "")));

        MetadataMatchResult result = new OnlineMetadataService(musicBrainz, acoustId, workspace)
                .match(new TrackMatchQuery(1L, "Tiger Song", "Tiger Band", "Tiger Album", "", "", "", "", ""));

        assertEquals(1, result.candidates().size());
        MetadataCandidate merged = result.candidates().get(0);
        assertEquals(97, merged.score());
        assertTrue(merged.source().contains("MusicBrainz"));
        assertTrue(merged.source().contains("AcoustID"));
        assertEquals("Tiger Album", merged.album(), "应保留 MusicBrainz 提供的专辑");
        assertTrue(result.acoustidUsed());
    }

    @Test
    void fallsBackToFileNameWhenTitleIsMissing() throws Exception {
        Workspace workspace = workspaceWithTrack("01 Tiger Song.mp3", tags("", "", ""));
        FakeMusicBrainz musicBrainz = new FakeMusicBrainz(List.of());
        FakeAcoustId acoustId = new FakeAcoustId(false, "未配置 AcoustID API Key", List.of());

        MetadataMatchResult result = new OnlineMetadataService(musicBrainz, acoustId, workspace)
                .match(new TrackMatchQuery(1L, "", "", "", "", "", "", "", ""));

        assertEquals("01 Tiger Song", result.keyword(), "无标题时应回退到去扩展名的文件名（BIZ-024）");
        assertEquals("01 Tiger Song", musicBrainz.lastKeyword);
        assertFalse(result.acoustidAvailable());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("未配置 AcoustID")));
    }

    @Test
    void continuesWithAcoustIdWhenMusicBrainzFails() throws Exception {
        Workspace workspace = workspaceWithTrack("song.mp3", tags("Song", "Artist", "Album"));
        FakeMusicBrainz musicBrainz = new FakeMusicBrainz(List.of());
        musicBrainz.failure = new OnlineAccessException("HTTP 503");
        FakeAcoustId acoustId = new FakeAcoustId(true, "", List.of(candidate("r2", "AcoustID", 88, "Song", "Album")));

        MetadataMatchResult result = new OnlineMetadataService(musicBrainz, acoustId, workspace)
                .match(new TrackMatchQuery(1L, "Song", "Artist", "Album", "", "", "", "", ""));

        assertEquals(1, result.candidates().size());
        assertTrue(result.candidates().get(0).source().contains("AcoustID"));
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("MusicBrainz 检索失败")));
    }

    @Test
    void lyricsSearchUsesCurrentValuesAndDuration() throws Exception {
        Workspace workspace = workspaceWithTrack("song.mp3", tags("Song", "Artist", "Album"));
        FakeLyrics lyrics = new FakeLyrics(List.of(new LyricsCandidate(
                "lrclib-1", "LRCLIB", "Song", "Artist", "Album", "3:20", true, 95, "[00:01.00]line")));

        LyricsMatchResult result = new LyricsMatchService(List.of(lyrics), workspace)
                .search(new LyricsMatchQuery(1L, "Song", "Artist", "Album", ""));

        assertEquals(1, result.candidates().size());
        assertEquals("Song", lyrics.lastQuery.title());
        assertEquals(200, lyrics.lastQuery.durationSeconds(), "应把曲目时长传给歌词源用于评分");
    }

    @Test
    void lyricsSearchReportsProviderFailure() throws Exception {
        Workspace workspace = workspaceWithTrack("song.mp3", tags("Song", "Artist", "Album"));
        FakeLyrics lyrics = new FakeLyrics(List.of());
        lyrics.failure = new OnlineAccessException("连接失败");

        LyricsMatchResult result = new LyricsMatchService(List.of(lyrics), workspace)
                .search(new LyricsMatchQuery(1L, "Song", "Artist", "", ""));

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("歌词检索失败")));
    }

    @Test
    void lyricsKeepsThreePerSourceAndDropsIdenticalContent() throws Exception {
        Workspace workspace = workspaceWithTrack("song.mp3", tags("Song", "Artist", "Album"));
        List<LyricsCandidate> lrclib = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            lrclib.add(new LyricsCandidate("lrclib-" + i, "LRCLIB", "Song", "Artist", "Album",
                    "3:20", true, 96 - i, "[00:01.00]line " + i));
        }
        List<LyricsCandidate> lrcapi = new java.util.ArrayList<>();
        lrcapi.add(new LyricsCandidate("lrc-cx-dup", "LRCAPI", "Song", "Artist", "Album",
                "3:20", true, 93, "[00:01.00]line 3"));
        for (int i = 1; i <= 3; i++) {
            lrcapi.add(new LyricsCandidate("lrc-cx-" + i, "LRCAPI", "Song", "Artist", "Album",
                    "3:2" + i, true, 80, "[00:01.00]other " + i));
        }

        LyricsMatchResult result = new LyricsMatchService(
                List.of(new FakeLyrics(lrclib), new FakeLyrics(lrcapi)), workspace)
                .search(new LyricsMatchQuery(1L, "Song", "Artist", "Album", ""));

        assertEquals(3, result.candidates().stream()
                .filter(candidate -> "LRCLIB".equals(candidate.source())).count());
        assertEquals(2, result.candidates().stream()
                .filter(candidate -> "LRCAPI".equals(candidate.source())).count(),
                "lrcapi 第三条与 LRCLIB 内容相同，应去重");
        assertEquals(5, result.candidates().size());
    }

    private Workspace workspaceWithTrack(String fileName, TrackTags tags) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, "audio");
        Workspace workspace = new Workspace();
        workspace.addImported(file, tags, 200L);
        return workspace;
    }

    private static TrackTags tags(String title, String artist, String album) {
        return TrackTags.empty()
                .withValue(com.aiasti.tiger.domain.model.TagField.TITLE, title)
                .withValue(com.aiasti.tiger.domain.model.TagField.ARTIST, artist)
                .withValue(com.aiasti.tiger.domain.model.TagField.ALBUM, album);
    }

    private static MetadataCandidate candidate(String id, String source, int score, String title, String album) {
        return new MetadataCandidate(id, source, score, title, "Tiger Band", album, "Tiger Band",
                "1", "1", "2020", "", "release", "group", source + " detail");
    }

    private static final class FakeMusicBrainz implements MusicBrainzPort {
        private final List<MetadataCandidate> result;
        private RuntimeException failure;
        private String lastKeyword;

        private FakeMusicBrainz(List<MetadataCandidate> result) {
            this.result = result;
        }

        @Override
        public List<MetadataCandidate> search(String title, String artist, String album, String keyword) {
            this.lastKeyword = keyword;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeAcoustId implements AcoustIdPort {
        private final boolean available;
        private final String reason;
        private final List<MetadataCandidate> result;

        private FakeAcoustId(boolean available, String reason, List<MetadataCandidate> result) {
            this.available = available;
            this.reason = reason;
            this.result = result;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String unavailableReason() {
            return reason;
        }

        @Override
        public List<MetadataCandidate> lookup(Path file) {
            if (!available) {
                throw new OnlineAccessException(reason);
            }
            return result;
        }
    }

    private static final class FakeLyrics implements LyricsPort {
        private final List<LyricsCandidate> result;
        private RuntimeException failure;
        private LyricsQuery lastQuery;

        private FakeLyrics(List<LyricsCandidate> result) {
            this.result = result;
        }

        @Override
        public List<LyricsCandidate> search(LyricsQuery query) {
            this.lastQuery = query;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
