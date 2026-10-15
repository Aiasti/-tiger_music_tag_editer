package com.aiasti.tiger.api;

import java.util.List;

/** 导入请求体：待导入路径与是否递归子目录（BIZ-014）。 */
public record ImportRequest(List<String> paths, boolean recursive) {
}
