package com.aiasti.tiger.application.workspace;

import java.util.List;

/** 重命名预览结果（BIZ-029）：与提交清单同序的逐文件规划。 */
public record RenamePlan(List<RenamePlanItem> items) {
}
