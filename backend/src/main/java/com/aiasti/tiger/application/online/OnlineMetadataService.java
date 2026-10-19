package com.aiasti.tiger.application.online;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.application.workspace.Track;
import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.domain.model.MetadataCandidate;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.port.AcoustIdPort;
import com.aiasti.tiger.domain.port.MusicBrainzPort;

/**
 * 单曲元数据在线匹配（FR-5、DEV-004）。
 *
 * <p>MusicBrainz 文本检索为主，AcoustID 指纹为辅；同一录音同时命中时合并为一条候选。
 * 单个 Provider 失败只记入 warnings，不影响另一条链路。
 */
@Service
public class OnlineMetadataService {

    private static final int MAX_CANDIDATES = 8;

    private final MusicBrainzPort musicBrainz;
    private final AcoustIdPort acoustId;
    private final Workspace workspace;

    public OnlineMetadataService(MusicBrainzPort musicBrainz, AcoustIdPort acoustId, Workspace workspace) {
        this.musicBrainz = musicBrainz;
        this.acoustId = acoustId;
        this.workspace = workspace;
    }

    public MetadataMatchResult match(TrackMatchQuery query) {
        List<String> warnings = new ArrayList<>();
        Optional<Track> found = workspace.find(query.trackId());
        if (found.isEmpty()) {
            return new MetadataMatchResult(query.trackId(), "", List.of(),
                    List.of("曲目已不在工作区"), acoustId.isAvailable(), false);
        }
        Track track = found.get();
        String title = firstNonBlank(query.title(), tag(track, TagField.TITLE));
        String artist = firstNonBlank(query.artist(), tag(track, TagField.ARTIST));
        String album = firstNonBlank(query.album(), tag(track, TagField.ALBUM));
        String keyword = firstNonBlank(query.keyword(), title, fileNameBase(track.path()));
        // 显式关键词优先于标题：用户填写关键词时按关键词检索，否则按标题/艺术家检索
        String searchTitle = query.keyword() == null || query.keyword().isBlank() ? title : "";

        Map<String, MetadataCandidate> byRecording = new LinkedHashMap<>();
        try {
            for (MetadataCandidate candidate : musicBrainz.search(searchTitle, artist, album, keyword)) {
                byRecording.put(candidate.id(), candidate);
            }
        } catch (RuntimeException e) {
            warnings.add(failure("MusicBrainz", e));
        }

        boolean acoustidAvailable = acoustId.isAvailable();
        boolean acoustidUsed = false;
        if (acoustidAvailable) {
            try {
                for (MetadataCandidate candidate : acoustId.lookup(track.path())) {
                    acoustidUsed = true;
                    byRecording.merge(candidate.id(), candidate, MetadataCandidate::merge);
                }
            } catch (RuntimeException e) {
                warnings.add(failure("AcoustID", e));
            }
        } else {
            warnings.add(acoustId.unavailableReason());
        }

        List<MetadataCandidate> candidates = byRecording.values().stream()
                .sorted(Comparator.comparingInt(MetadataCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.title() == null ? "" : candidate.title()))
                .limit(MAX_CANDIDATES)
                .toList();
        if (candidates.isEmpty()) {
            warnings.add("未找到匹配候选，可尝试修改标题/艺术家或直接使用文件名检索");
        }
        return new MetadataMatchResult(
                query.trackId(), keyword, candidates, warnings, acoustidAvailable, acoustidUsed);
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

    private static String failure(String provider, RuntimeException e) {
        String reason = e.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = e.getClass().getSimpleName();
        }
        return provider + " 检索失败：" + reason;
    }
}
