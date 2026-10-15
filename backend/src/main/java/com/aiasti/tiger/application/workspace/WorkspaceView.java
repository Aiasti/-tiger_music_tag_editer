package com.aiasti.tiger.application.workspace;

import java.util.List;

/** 工作区快照视图（契约字段与前端约定一致）。 */
public record WorkspaceView(List<TrackView> tracks, List<SkippedFile> skipped) {
}
