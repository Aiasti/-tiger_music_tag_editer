package com.aiasti.tiger.application.workspace;

/**
 * 单个曲目的写回结果（DEV-003、BIZ-028）。
 *
 * @param id       曲目 id
 * @param outcome  结果状态契约值（见 {@link WriteOutcomeStatus}）
 * @param reason   面向用户的原因（非 {@code written} 时给出）
 * @param detail   {@code failed-manual} 的实际情况与建议动作
 * @param tags     写入后磁盘上的实际标签值（仅 {@code written} 时有值，供渲染层刷新最近落盘值）
 * @param filePath 操作后的实际路径（S2-2 与写入前一致）
 * @param hasCover 写入后文件是否含封面
 */
public record WriteOutcome(
        long id,
        String outcome,
        String reason,
        String detail,
        TagValues tags,
        String filePath,
        boolean hasCover) {

    static WriteOutcome written(long id, TagValues tags, String filePath, boolean hasCover) {
        return new WriteOutcome(id, WriteOutcomeStatus.WRITTEN.contractValue(), null, null, tags, filePath, hasCover);
    }

    static WriteOutcome notExecuted(long id, WriteOutcomeStatus status, String reason, String filePath) {
        return new WriteOutcome(id, status.contractValue(), reason, null, null, filePath, false);
    }

    static WriteOutcome failed(long id, WriteOutcomeStatus status, String reason, String detail, String filePath) {
        return new WriteOutcome(id, status.contractValue(), reason, detail, null, filePath, false);
    }
}
