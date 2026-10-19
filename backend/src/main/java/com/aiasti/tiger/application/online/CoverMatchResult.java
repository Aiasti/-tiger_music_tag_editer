package com.aiasti.tiger.application.online;

import java.util.List;

import com.aiasti.tiger.domain.model.CoverCandidate;

/** 单曲封面匹配结果。 */
public record CoverMatchResult(
        long trackId,
        String keyword,
        List<CoverCandidate> candidates,
        List<String> warnings) {
}
