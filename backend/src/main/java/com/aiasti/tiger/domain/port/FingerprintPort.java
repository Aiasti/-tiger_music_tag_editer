package com.aiasti.tiger.domain.port;

import java.nio.file.Path;
import java.util.Optional;

import com.aiasti.tiger.domain.model.Fingerprint;

/** 本地音频指纹端口（Chromaprint / fpcalc，DEV-004）。 */
public interface FingerprintPort {

    boolean isAvailable();

    /** 生成指纹；音频过短、指纹为空或工具不可用时返回空。 */
    Optional<Fingerprint> create(Path file);
}
