package com.aiasti.tiger.application.workspace;

/**
 * 单个曲目的写回结果（DEV-003、BIZ-028、BIZ-030）。
 *
 * @param id       曲目 id
 * @param outcome  结果状态契约值（见 {@link WriteOutcomeStatus}）
 * @param reason   面向用户的原因（非 {@code written} 时给出）
 * @param detail   {@code failed-manual} 的实际情况与建议动作
 * @param tags     写入后磁盘上的实际标签值（{@code written}/{@code partial} 时有值，供刷新最近落盘值）
 * @param filePath 操作后的实际路径
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

    static WriteOutcome partial(long id, TagValues tags, String reason, String filePath, boolean hasCover) {
        return new WriteOutcome(id, WriteOutcomeStatus.PARTIAL.contractValue(), reason, null, tags, filePath, hasCover);
    }

    static WriteOutcome notExecuted(long id, WriteOutcomeStatus status, String reason, String filePath) {
        return new WriteOutcome(id, status.contractValue(), reason, null, null, filePath, false);
    }

    static WriteOutcome failed(long id, WriteOutcomeStatus status, String reason, String detail, String filePath) {
        return new WriteOutcome(id, status.contractValue(), reason, detail, null, filePath, false);
    }
}
