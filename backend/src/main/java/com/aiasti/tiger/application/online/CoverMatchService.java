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
import com.aiasti.tiger.domain.model.MetadataCandidate;
import com.aiasti.tiger.domain.model.TagField;
import com.aiasti.tiger.domain.port.CoverSearchPort;
import com.aiasti.tiger.domain.port.MusicBrainzPort;

/**
 * 单曲封面匹配（FR-6、DEV-004）。
 *
 * <p>先用元数据匹配（标题/艺术家/专辑 + AcoustID 指纹）与专辑名检索得到发行版，
 * 再用 iTunes Search API 按专辑名/艺术家/标题获取可直接下载的封面；
 * Cover Art Archive 候选作为后备。只返回封面候选，不读取、不修改歌词。
 */
@Service
public class CoverMatchService {

    private static final int MAX_CANDIDATES = 5;

    private final OnlineMetadataService metadataService;
    private final MusicBrainzPort musicBrainz;
    private final CoverSearchPort coverSearch;
    private final Workspace workspace;

    public CoverMatchService(OnlineMetadataService metadataService,
                             MusicBrainzPort musicBrainz,
                             CoverSearchPort coverSearch,
                             Workspace workspace) {
        this.metadataService = metadataService;
        this.musicBrainz = musicBrainz;
        this.coverSearch = coverSearch;
        this.workspace = workspace;
    }

    public CoverMatchResult match(TrackMatchQuery query) {
        MetadataMatchResult metadata = metadataService.match(query);
        List<String> warnings = new ArrayList<>(metadata.warnings());
        warnings.removeIf(message -> message.contains("未找到匹配候选"));

        String album = firstNonBlank(query.album(), currentTag(query.trackId(), TagField.ALBUM));
        String artist = firstNonBlank(query.artist(), currentTag(query.trackId(), TagField.ARTIST));
        String title = firstNonBlank(query.title(), currentTag(query.trackId(), TagField.TITLE), metadata.keyword());

        Map<String, CoverCandidate> byKey = new LinkedHashMap<>();
        metadata.candidates().forEach(candidate -> addMetadata(byKey, candidate));
        if (!album.isBlank() || !artist.isBlank()) {
            try {
                for (MetadataCandidate candidate : musicBrainz.searchReleases(album, artist)) {
                    addMetadata(byKey, candidate);
                }
            } catch (RuntimeException e) {
                warnings.add("MusicBrainz 专辑检索失败：" + reasonOf(e));
            }
        }
        try {
            for (CoverCandidate candidate : coverSearch.search(album, artist, title)) {
                byKey.merge(candidate.id(), candidate, CoverMatchService::merge);
            }
        } catch (RuntimeException e) {
            warnings.add("iTunes 封面检索失败：" + reasonOf(e));
        }

        List<CoverCandidate> candidates = byKey.values().stream()
                .sorted(Comparator
                        .comparingInt((CoverCandidate candidate) ->
                                candidate.imageUrl() == null || candidate.imageUrl().isBlank() ? 1 : 0)
                        .thenComparing(Comparator.comparingInt(CoverCandidate::score).reversed()))
                .limit(MAX_CANDIDATES)
                .toList();
        if (candidates.isEmpty()) {
            warnings.add("未找到可用封面候选，可尝试修改专辑名/艺术家或使用关键词检索");
        }
        return new CoverMatchResult(query.trackId(), metadata.keyword(), candidates, warnings);
    }

    private static void addMetadata(Map<String, CoverCandidate> byKey, MetadataCandidate candidate) {
        if (!hasCoverSource(candidate)) {
            return;
        }
        String key = "mb:" + firstNonBlank(candidate.releaseGroupId(), candidate.releaseId());
        CoverCandidate cover = new CoverCandidate(
                key,
                candidate.source() + " · Cover Art Archive",
                candidate.score(),
                candidate.title(),
                candidate.artist(),
                candidate.album(),
                candidate.year(),
                candidate.releaseId(),
                candidate.releaseGroupId(),
                "",
                candidate.detail());
        byKey.merge(key, cover, CoverMatchService::merge);
    }

    private static CoverCandidate merge(CoverCandidate first, CoverCandidate second) {
        return new CoverCandidate(
                first.id(),
                mergeSource(first.source(), second.source()),
                Math.max(first.score(), second.score()),
                prefer(first.title(), second.title()),
                prefer(first.artist(), second.artist()),
                prefer(first.album(), second.album()),
                prefer(first.year(), second.year()),
                prefer(first.releaseId(), second.releaseId()),
                prefer(first.releaseGroupId(), second.releaseGroupId()),
                prefer(first.imageUrl(), second.imageUrl()),
                prefer(first.detail(), second.detail()));
    }

    private static String mergeSource(String first, String second) {
        String suffix = " · Cover Art Archive";
        String a = first.endsWith(suffix) ? first.substring(0, first.length() - suffix.length()) : first;
        String b = second.endsWith(suffix) ? second.substring(0, second.length() - suffix.length()) : second;
        if (a.contains(b)) {
            return a + suffix;
        }
        if (b.contains(a)) {
            return b + suffix;
        }
        return a + " + " + b + suffix;
    }

    private static String prefer(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }

    private static boolean hasCoverSource(MetadataCandidate candidate) {
        return (candidate.releaseGroupId() != null && !candidate.releaseGroupId().isBlank())
                || (candidate.releaseId() != null && !candidate.releaseId().isBlank());
    }

    private String currentTag(long trackId, TagField field) {
        Optional<Track> track = workspace.find(trackId);
        return track.map(value -> value.current().value(field).orElse("")).orElse("");
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
