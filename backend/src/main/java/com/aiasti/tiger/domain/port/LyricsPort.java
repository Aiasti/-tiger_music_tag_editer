package com.aiasti.tiger.domain.port;

import java.util.List;

import com.aiasti.tiger.domain.model.LyricsCandidate;
import com.aiasti.tiger.domain.model.LyricsQuery;

/** 在线歌词源端口（FR-6、DEV-004）。 */
public interface LyricsPort {

    List<LyricsCandidate> search(LyricsQuery query);
}
