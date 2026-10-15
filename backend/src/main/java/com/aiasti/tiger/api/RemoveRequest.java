package com.aiasti.tiger.api;

import java.util.List;

/** 移出工作区请求体（BIZ-015）：仅会话内移除，不改动磁盘文件。 */
public record RemoveRequest(List<Long> ids) {
}
