package com.aiasti.tiger.api;

import java.util.List;

import com.aiasti.tiger.application.workspace.WriteCommand;

/** 写回请求体（BIZ-028）：每首曲目的整份当前标签值，后端按落盘基线自算差异。 */
public record WriteBackRequest(List<WriteCommand> tracks) {
}
