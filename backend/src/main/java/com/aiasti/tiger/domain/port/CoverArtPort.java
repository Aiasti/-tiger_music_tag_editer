package com.aiasti.tiger.domain.port;

import java.util.Optional;

import com.aiasti.tiger.domain.model.CoverImage;

/** 封面图库端口（Cover Art Archive，DEV-004）。 */
public interface CoverArtPort {

    /** 按发行组/发行版 ID 获取正面封面（front-500）；找不到返回空。 */
    Optional<CoverImage> front(String releaseGroupId, String releaseId);

    /** 按可信图片地址下载封面（用于 iTunes 等源）；地址不在允许列表时返回空。 */
    Optional<CoverImage> fetch(String imageUrl);
}
