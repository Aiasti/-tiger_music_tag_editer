package com.aiasti.tiger.application.online;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.aiasti.tiger.application.workspace.Track;
import com.aiasti.tiger.application.workspace.Workspace;
import com.aiasti.tiger.domain.model.CoverCandidate;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.port.CoverSearchPort;

/**
 * 单曲封面匹配（FR-6、DEV-004）。
 *
 * <p>汇聚 iTunes（US/JP/HK）与 lrcapi 的封面候选；每个源保留评分最高的 3 条，
 * 合并去重后按评分保留最多 5 条，只返回封面候选，不读取、不修改歌词。
 */
@Service
public class CoverMatchService {

    private static final int PER_SOURCE_LIMIT = 3;
    private static final int MAX_CANDIDATES = 5;

    private final List<CoverSearchPort> coverSearchPorts;
    private final Workspace workspace;

    public CoverMatchService(List<CoverSearchPort> coverSearchPorts, Workspace workspace) {
        this.coverSearchPorts = coverSearchPorts;
        this.workspace = workspace;
    }

    public CoverMatchResult match(TrackMatchQuery query) {
        List<String> warnings = new ArrayList<>();

        String album = firstNonBlank(query.album(), currentTag(query.trackId(), TagField.ALBUM));
        String artist = firstNonBlank(query.artist(), currentTag(query.trackId(), TagField.ARTIST));
        String title = firstNonBlank(query.title(), currentTag(query.trackId(), TagField.TITLE), query.keyword());
        String keyword = firstNonBlank(query.keyword(), title, trackBaseName(query.trackId()));

        List<CoverCandidate> merged = new ArrayList<>();
        for (CoverSearchPort port : coverSearchPorts) {
            try {
                merged.addAll(port.search(album, artist, title).stream()
                        .sorted(Comparator.comparingInt(CoverCandidate::score).reversed())
                        .limit(PER_SOURCE_LIMIT)
                        .toList());
            } catch (RuntimeException e) {
                warnings.add(port.sourceName() + " 封面检索失败：" + reasonOf(e));
            }
        }

        List<CoverCandidate> candidates = dedupeAndSort(merged);
        if (candidates.isEmpty()) {
            warnings.add("未找到可用封面候选，可尝试修改专辑名/艺术家或使用关键词检索");
        }
        return new CoverMatchResult(query.trackId(), keyword, candidates, warnings);
    }

    private static List<CoverCandidate> dedupeAndSort(List<CoverCandidate> candidates) {
        candidates.sort(Comparator.comparingInt(CoverCandidate::score).reversed());
        Map<String, CoverCandidate> unique = new LinkedHashMap<>();
        for (CoverCandidate candidate : candidates) {
            String key = candidate.imageUrl() == null || candidate.imageUrl().isBlank()
                    ? candidate.id() : candidate.imageUrl().trim();
            unique.putIfAbsent(key, candidate);
        }
        return unique.values().stream().limit(MAX_CANDIDATES).toList();
    }

    private String currentTag(long trackId, TagField field) {
        Optional<Track> track = workspace.find(trackId);
        return track.map(value -> value.current().value(field).orElse("")).orElse("");
    }

    /** 去扩展名的文件名，作为没有标题时的检索回退（BIZ-024）。 */
    private String trackBaseName(long trackId) {
        return workspace.find(trackId)
                .map(track -> {
                    String name = track.path().getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    return dot > 0 ? name.substring(0, dot) : name;
                })
                .orElse("");
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
