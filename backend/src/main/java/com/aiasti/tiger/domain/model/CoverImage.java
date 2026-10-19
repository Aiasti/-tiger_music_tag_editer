package com.aiasti.tiger.domain.model;

/** 封面图片数据（来自 Cover Art Archive）。 */
public record CoverImage(byte[] data, String mimeType) {

    public CoverImage {
        data = data == null ? new byte[0] : data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
