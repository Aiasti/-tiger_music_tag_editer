package com.aiasti.tiger.api;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aiasti.tiger.application.online.CoverMatchResult;
import com.aiasti.tiger.application.online.CoverMatchService;
import com.aiasti.tiger.application.online.LyricsMatchQuery;
import com.aiasti.tiger.application.online.LyricsMatchResult;
import com.aiasti.tiger.application.online.LyricsMatchService;
import com.aiasti.tiger.application.online.MetadataMatchResult;
import com.aiasti.tiger.application.online.OnlineMetadataService;
import com.aiasti.tiger.application.online.TrackMatchQuery;
import com.aiasti.tiger.domain.exception.OnlineAccessException;
import com.aiasti.tiger.domain.model.CoverImage;
import com.aiasti.tiger.domain.port.CoverArtPort;

/**
 * 在线匹配接口（FR-5、FR-6、DEV-004）。
 *
 * <p>按单曲调用，便于前端逐曲显示进度并随时停止；api 层不直接访问第三方源。
 */
@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final OnlineMetadataService metadataService;
    private final LyricsMatchService lyricsService;
    private final CoverMatchService coverMatchService;
    private final CoverArtPort coverArtPort;

    public MatchController(OnlineMetadataService metadataService,
                           LyricsMatchService lyricsService,
                           CoverMatchService coverMatchService,
                           CoverArtPort coverArtPort) {
        this.metadataService = metadataService;
        this.lyricsService = lyricsService;
        this.coverMatchService = coverMatchService;
        this.coverArtPort = coverArtPort;
    }

    /** 单曲元数据候选：MusicBrainz 文本检索 + AcoustID 指纹（可用时）。 */
    @PostMapping("/metadata")
    public MetadataMatchResult metadata(@RequestBody MetadataMatchRequest request) {
        long trackId = requireTrackId(request.trackId());
        return metadataService.match(TrackMatchQuery.of(
                trackId,
                request.title(),
                request.artist(),
                request.album(),
                request.albumArtist(),
                request.trackNo(),
                request.discNo(),
                request.year(),
                request.keyword()));
    }

    /** 单曲歌词候选：首版接入 LRCLIB。 */
    @PostMapping("/lyrics")
    public LyricsMatchResult lyrics(@RequestBody LyricsMatchRequest request) {
        long trackId = requireTrackId(request.trackId());
        return lyricsService.search(new LyricsMatchQuery(
                trackId,
                request.title(),
                request.artist(),
                request.album(),
                request.keyword()));
    }

    /** 单曲封面候选：复用元数据匹配得到发行版，再交给 Cover Art Archive 取图。 */
    @PostMapping("/cover")
    public CoverMatchResult cover(@RequestBody MetadataMatchRequest request) {
        long trackId = requireTrackId(request.trackId());
        return coverMatchService.match(TrackMatchQuery.of(
                trackId,
                request.title(),
                request.artist(),
                request.album(),
                request.albumArtist(),
                request.trackNo(),
                request.discNo(),
                request.year(),
                request.keyword()));
    }

    /** 封面图片代理：前端带 token 拉取，避免渲染层跨源与第三方鉴权问题。 */
    @GetMapping("/cover/image")
    public ResponseEntity<byte[]> coverImage(
            @RequestParam(required = false) String releaseGroupId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) String imageUrl) {
        boolean hasRelease = (releaseGroupId != null && !releaseGroupId.isBlank())
                || (releaseId != null && !releaseId.isBlank());
        boolean hasUrl = imageUrl != null && !imageUrl.isBlank();
        if (!hasRelease && !hasUrl) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "releaseGroupId / releaseId / imageUrl 不能同时为空");
        }
        try {
            Optional<CoverImage> image = hasUrl
                    ? coverArtPort.fetch(imageUrl)
                    : coverArtPort.front(releaseGroupId, releaseId);
            return image.map(value -> ResponseEntity.ok()
                            .contentType(mediaType(value))
                            .body(value.data()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (OnlineAccessException e) {
            // 第三方封面源不可达时按「无封面」处理，不影响其余候选
            return ResponseEntity.notFound().build();
        }
    }

    private static MediaType mediaType(CoverImage image) {
        try {
            return MediaType.parseMediaType(image.mimeType());
        } catch (RuntimeException e) {
            return MediaType.IMAGE_JPEG;
        }
    }

    private static long requireTrackId(Long trackId) {
        if (trackId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trackId 不能为空");
        }
        return trackId;
    }
}
