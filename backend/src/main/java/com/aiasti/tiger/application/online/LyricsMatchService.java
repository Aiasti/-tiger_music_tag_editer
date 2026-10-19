package com.aiasti.tiger.application.online;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p>同时查询 LRCLIB 与 lrcapi，每个源保留置信度最高的 3 条，合并去重后最多返回 6 条；
 * 关键词为空时按标题、再回退到去扩展名的文件名（BIZ-024）。
 */
@Service
public class LyricsMatchService {

    private static final int PER_SOURCE_LIMIT = 3;
    private static final int TOTAL_LIMIT = 6;

    private final List<LyricsPort> lyricsPorts;
    private final Workspace workspace;

    public LyricsMatchService(List<LyricsPort> lyricsPorts, Workspace workspace) {
        this.lyricsPorts = lyricsPorts;
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
        LyricsQuery lyricsQuery = new LyricsQuery(title, artist, album, keyword, track.durationSeconds());

        List<LyricsCandidate> merged = new ArrayList<>();
        for (LyricsPort port : lyricsPorts) {
            try {
                List<LyricsCandidate> sourceCandidates = port.search(lyricsQuery).stream()
                        .sorted(Comparator.comparingInt(LyricsCandidate::confidence).reversed())
                        .limit(PER_SOURCE_LIMIT)
                        .toList();
                merged.addAll(sourceCandidates);
            } catch (RuntimeException e) {
                warnings.add(port.sourceName() + " 歌词检索失败：" + reasonOf(e));
            }
        }

        List<LyricsCandidate> candidates = dedupeAndSort(merged);
        if (candidates.isEmpty() && warnings.isEmpty()) {
            warnings.add("未找到歌词候选，可尝试调整检索关键词");
        }
        return new LyricsMatchResult(query.trackId(), keyword, candidates, warnings);
    }

    /** 合并后按置信度排序；同分时 LRCLIB 优先，再 LRCAPI。 */
    private static List<LyricsCandidate> dedupeAndSort(List<LyricsCandidate> candidates) {
        candidates.sort(Comparator
                .comparingInt(LyricsCandidate::confidence).reversed()
                .thenComparingInt(candidate -> sourceRank(candidate.source())));
        Map<String, LyricsCandidate> unique = new LinkedHashMap<>();
        for (LyricsCandidate candidate : candidates) {
            // 同曲不同版本（时长/歌词内容不同）应保留，仅折叠内容相同的重复项
            String key = normalize(candidate.title()) + "|" + normalize(candidate.artist())
                    + "|" + normalize(candidate.duration()) + "|" + normalize(candidate.preview());
            unique.putIfAbsent(key, candidate);
        }
        return unique.values().stream().limit(TOTAL_LIMIT).toList();
    }

    private static int sourceRank(String source) {
        if ("LRCLIB".equalsIgnoreCase(source)) {
            return 0;
        }
        return "LRCAPI".equalsIgnoreCase(source) ? 1 : 2;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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

    private static String reasonOf(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage();
    }
}
