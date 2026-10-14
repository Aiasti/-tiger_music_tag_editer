package com.aiasti.tiger.infrastructure.tag;

/** 依据文件头识别封面图片的 MIME 类型。 */
final class ImageMimeTypes {

    private static final String DEFAULT_MIME = "image/jpeg";

    private ImageMimeTypes() {
    }

    static String detect(byte[] data) {
        if (data == null || data.length < 3) {
            return DEFAULT_MIME;
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N') {
            return "image/png";
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        return DEFAULT_MIME;
    }
}
