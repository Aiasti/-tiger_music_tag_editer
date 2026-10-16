package com.aiasti.tiger.application.workspace;

/**
 * 写回结果状态（DEV-003 结论 5、BIZ-028）；契约值与前端的写回结果展示一致。
 *
 * <p>S2-2 只写标签、不移动文件，故不产生「部分完成（标签已写、路径未改）」，该状态由 S3 引入。
 */
public enum WriteOutcomeStatus {

    /** 已写回：标签已写入并通过校验。 */
    WRITTEN("written"),
    /** 未执行：文件被执行前被外部修改，按 DEV-003 结论 1 阻止该文件。 */
    STALE("stale"),
    /** 未执行：预检或恢复材料准备未通过。 */
    BLOCKED("blocked"),
    /** 失败（已恢复原状）：写回或校验失败，已用恢复材料还原为执行前状态。 */
    FAILED_RESTORED("failed-restored"),
    /** 失败（未恢复，需人工处理）：补偿失败，磁盘状态需人工核对。 */
    FAILED_MANUAL("failed-manual");

    private final String contractValue;

    WriteOutcomeStatus(String contractValue) {
        this.contractValue = contractValue;
    }

    public String contractValue() {
        return contractValue;
    }
}
