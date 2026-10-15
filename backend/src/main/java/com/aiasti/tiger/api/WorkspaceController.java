package com.aiasti.tiger.api;

import com.aiasti.tiger.application.ImportWorkspaceService;
import com.aiasti.tiger.application.workspace.WorkspaceView;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作区接口（FR-1）：导入与查询。api 层不直接读写文件，经 application 调用 domain 端口。 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final ImportWorkspaceService importWorkspaceService;

    public WorkspaceController(ImportWorkspaceService importWorkspaceService) {
        this.importWorkspaceService = importWorkspaceService;
    }

    @PostMapping("/import")
    public WorkspaceView importPaths(@RequestBody ImportRequest request) {
        return importWorkspaceService.importPaths(request.paths(), request.recursive());
    }

    @GetMapping
    public WorkspaceView current() {
        return importWorkspaceService.currentView();
    }
}
