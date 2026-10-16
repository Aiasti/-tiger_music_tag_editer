package com.aiasti.tiger.application.workspace;

import java.util.List;

/** 写回结果（BIZ-028）：与提交清单同序的逐文件结果。 */
public record WriteBackResult(List<WriteOutcome> outcomes) {
}
