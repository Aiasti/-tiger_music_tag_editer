package com.aiasti.tiger.domain.port;

import java.nio.file.Path;
import java.util.List;

import com.aiasti.tiger.domain.model.MetadataCandidate;

/** AcoustID 指纹查询端口（FR-5、DEV-004）。 */
public interface AcoustIdPort {

    /** 是否已配置 API Key 且能找到 fpcalc。 */
    boolean isAvailable();

    /** 不可用原因；可用时为空串。 */
    String unavailableReason();

    /** 生成指纹并查询 AcoustID；不可用时抛出 {@code OnlineAccessException}。 */
    List<MetadataCandidate> lookup(Path file);
}
