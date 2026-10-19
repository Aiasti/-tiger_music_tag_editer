package com.aiasti.tiger.domain.port;

import java.util.List;

import com.aiasti.tiger.domain.model.MetadataCandidate;

/** MusicBrainz 文本检索端口（FR-5、DEV-004）。 */
public interface MusicBrainzPort {

    /**
     * 按标题/艺术家检索录音；标题为空时使用 keyword（通常是去扩展名的文件名，BIZ-024）。
     * album 仅用于在多个发行版中优先选择与当前工作区一致的那一个。
     */
    List<MetadataCandidate> search(String title, String artist, String album, String keyword);

    /** 按专辑名/艺术家检索发行版（用于封面匹配的专辑名回退）；默认不实现。 */
    default List<MetadataCandidate> searchReleases(String album, String artist) {
        return List.of();
    }
}
