package com.aiasti.tiger.application.online;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.application.workspace.Track;
import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.port.LyricsPort;

/**
 * 单曲歌词在线检索（FR-6、DEV-004）。
 *
 * <p>首版接入 LRCLIB；关键词为空时按标题、再回退到去扩展名的文件名（BIZ-024）。
 */
@Service
public class LyricsMatchService {

    private final LyricsPort lyricsPort;
    private final Workspace workspace;

    public LyricsMatchService(LyricsPort lyricsPort, Workspace workspace) {
        this.lyricsPort = lyricsPort;
        this.workspace = workspace;
    }

    public LyricsMatchResult search(LyricsMatchQuery query) {
        List<String> warnings = new ArrayList<>();
        Optional<Track> found = workspace.find(query.trackId());
        if (found.isEmpty()) {
            return new LyricsMatchResult(query.trackId(), "", List.of(), List.of("曲目已不在工作区"));
        }
        Track track = found.get();
        String title = firstNonBlank(query.title(), tag(track, TagField.TITLE));
        String artist = firstNonBlank(query.artist(), tag(track, TagField.ARTIST));
        String album = firstNonBlank(query.album(), tag(track, TagField.ALBUM));
        String keyword = firstNonBlank(query.keyword(), title, fileNameBase(track.path()));

        List<LyricsCandidate> candidates;
        try {
            candidates = lyricsPort.search(
                    new LyricsQuery(title, artist, album, keyword, track.durationSeconds()));
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName() : e.getMessage();
            warnings.add("歌词检索失败：" + reason);
            candidates = List.of();
        }
        if (candidates.isEmpty() && warnings.isEmpty()) {
            warnings.add("未找到歌词候选，可尝试调整检索关键词");
        }
        return new LyricsMatchResult(query.trackId(), keyword, candidates, warnings);
    }

    private static String tag(Track track, TagField field) {
        return track.current().value(field).orElse("");
    }

    private static String fileNameBase(Path path) {
        Path name = path.getFileName();
        if (name == null) {
            return "";
        }
        String value = name.toString();
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
