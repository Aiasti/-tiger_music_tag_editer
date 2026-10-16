package com.aiasti.tiger.application.workspace;

/**
 * 单个曲目的重命名预览项（FR-7、BIZ-029）。
 *
 * @param id       曲目 id
 * @param oldPath  当前完整路径
 * @param newPath  规划后的完整路径
 * @param oldName  当前文件名
 * @param newName  规划后的文件名
 * @param changed  目标是否与当前不同
 * @param adjusted 是否因冲突被自动加序号
 * @param note     给用户的说明（冲突调整、批内顺序调整等），无则 {@code null}
 */
public record RenamePlanItem(
        long id,
        String oldPath,
        String newPath,
        String oldName,
        String newName,
        boolean changed,
        boolean adjusted,
        String note) {
}
