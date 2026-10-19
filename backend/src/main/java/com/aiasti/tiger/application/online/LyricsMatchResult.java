package com.aiasti.tiger.application.online;

import java.util.List;

import com.aiasti.tiger.domain.model.LyricsCandidate;

/** 单曲歌词检索结果。 */
public record LyricsMatchResult(
        long trackId,
        String keyword,
        List<LyricsCandidate> candidates,
        List<String> warnings) {
}
