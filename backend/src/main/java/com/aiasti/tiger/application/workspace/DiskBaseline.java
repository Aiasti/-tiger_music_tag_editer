package com.aiasti.tiger.application.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 最近成功落盘值（DEV-002、DEV-003）：文件大小与修改时间构成的指纹。
 *
 * <p>导入成功时记录、成功写回后更新，用于写回预检（外部修改检测）、差异基准与定位文件，
 * 不作为回滚目标。属性读取失败时以 {@link #UNKNOWN} 表示，预检按不通过处理。
 */
public record DiskBaseline(long modifiedMillis, long sizeBytes) {

    /** 基线不可用：文件属性读取失败。 */
    public static final DiskBaseline UNKNOWN = new DiskBaseline(-1L, -1L);

    /** 记录指定文件当前的落盘基线。 */
    public static DiskBaseline read(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new DiskBaseline(attributes.lastModifiedTime().toMillis(), attributes.size());
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    /** 基线是否可用于外部修改比对。 */
    public boolean available() {
        return modifiedMillis >= 0L && sizeBytes >= 0L;
    }

    /** 与另一基线是否指向同一文件状态（两者都须可用）。写回预检用它判断文件是否被外部改动。 */
    public boolean sameAs(DiskBaseline other) {
        return other != null
                && available()
                && other.available()
                && modifiedMillis == other.modifiedMillis
                && sizeBytes == other.sizeBytes;
    }
}
