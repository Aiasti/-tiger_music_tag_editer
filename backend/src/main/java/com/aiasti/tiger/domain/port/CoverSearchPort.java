package com.aiasti.tiger.domain.port;

import java.util.List;

import com.aiasti.tiger.domain.model.CoverCandidate;

/** 封面候选检索端口（按专辑名/艺术家/标题，如 iTunes Search API）。 */
public interface CoverSearchPort {

    List<CoverCandidate> search(String album, String artist, String title);

    /** 用户可见的封面源名称，用于失败提示。 */
    default String sourceName() {
        return getClass().getSimpleName();
    }
}
