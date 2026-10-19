package com.aiasti.tiger.application.online;

import java.util.List;

import com.aiasti.tiger.domain.model.MetadataCandidate;

/** 单曲元数据匹配结果。 */
public record MetadataMatchResult(
        long trackId,
        String keyword,
        List<MetadataCandidate> candidates,
        List<String> warnings,
        boolean acoustidAvailable,
        boolean acoustidUsed) {
}
