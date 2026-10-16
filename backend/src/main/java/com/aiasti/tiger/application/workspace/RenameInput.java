package com.aiasti.tiger.application.workspace;

/** 重命名预览的单曲输入（BIZ-029）：编号所需字段来自渲染层当前编辑值。 */
public record RenameInput(long id, String trackNo, String title) {
}
