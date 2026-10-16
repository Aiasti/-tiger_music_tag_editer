package com.aiasti.tiger.api;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiasti.tiger.application.ImportWorkspaceService;
import com.aiasti.tiger.application.workspace.CoverData;
import com.aiasti.tiger.application.workspace.RenamePlan;
import com.aiasti.tiger.application.workspace.RenamePreviewService;
import com.aiasti.tiger.application.workspace.WorkspaceView;
import com.aiasti.tiger.application.workspace.WriteBackResult;
import com.aiasti.tiger.application.workspace.WriteBackService;

/** 工作区接口（FR-1）：导入、查询、移出曲目、内嵌封面、写回与重命名预览。api 层不直接读写文件，经 application 调用 domain 端口。 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final ImportWorkspaceService importWorkspaceService;
    private final WriteBackService writeBackService;
    private final RenamePreviewService renamePreviewService;

    public WorkspaceController(ImportWorkspaceService importWorkspaceService,
                               WriteBackService writeBackService,
                               RenamePreviewService renamePreviewService) {
        this.importWorkspaceService = importWorkspaceService;
        this.writeBackService = writeBackService;
        this.renamePreviewService = renamePreviewService;
    }

    @PostMapping("/import")
    public WorkspaceView importPaths(@RequestBody ImportRequest request) {
        return importWorkspaceService.importPaths(request.paths(), request.recursive());
    }

    @GetMapping
    public WorkspaceView current() {
        return importWorkspaceService.currentView();
    }

    /** 按 id 将曲目移出工作区（BIZ-015）：不改磁盘，废弃其未提交编辑。 */
    @PostMapping("/remove")
    public WorkspaceView remove(@RequestBody RemoveRequest request) {
        return importWorkspaceService.removeTracks(request.ids());
    }

    /** 清空工作区（BIZ-017）：供桌面壳在应用启动时调用，保证「关闭重开工作区为空」。 */
    @PostMapping("/clear")
    public WorkspaceView clear() {
        return importWorkspaceService.clearWorkspace();
    }

    /** 写回标签（FR-8、DEV-003、BIZ-028）：逐文件五阶段执行，返回同序的逐项结果。 */
    @PostMapping("/commit")
    public WriteBackResult commit(@RequestBody WriteBackRequest request) {
        return writeBackService.writeAll(request.tracks());
    }

    /** 重命名/归档预览（FR-7、BIZ-030）：按当前编辑值与模板规划目标路径与冲突标注，不移动文件。 */
    @PostMapping("/rename-preview")
    public RenamePlan renamePreview(@RequestBody RenamePreviewRequest request) {
        return renamePreviewService.preview(request);
    }

    /** 取曲目内嵌封面；无封面返回 404（按需获取，避免列表返回体积膨胀）。 */
    @GetMapping("/tracks/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable long id) {
        Optional<CoverData> cover = importWorkspaceService.cover(id);
        if (cover.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CoverData data = cover.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.mimeType()))
                .body(data.data());
    }
}
