package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.security.service.Phase2ProbeService;
import cn.edu.gpnu.platform.security.vo.DataScopeProbeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Phase2权限验证")
@RestController
@RequestMapping("/api/phase2/probe")
@RequiredArgsConstructor
public class Phase2ProbeController {

    private final Phase2ProbeService probeService;

    @Operation(summary = "AT-13 学生列表数据范围验证")
    @PreAuthorize("@pms.has('student:view')")
    @DataScope(permission = "student:view")
    @GetMapping("/students")
    public Result<List<DataScopeProbeVO>> students() {
        return Result.ok(probeService.listStudents());
    }

    @Operation(summary = "AT-13 学生本人访问验证")
    @PreAuthorize("@pms.has('student:view')")
    @DataScope(permission = "student:view")
    @GetMapping("/students/{studentId}")
    public Result<DataScopeProbeVO> student(@PathVariable Long studentId) {
        return Result.ok(probeService.getStudent(studentId));
    }

    @Operation(summary = "RBAC 证书生成越权反例")
    @PreAuthorize("@pms.has('cert:generate')")
    @AuditLog(bizType = "cert", operation = "generateProbe")
    @PostMapping("/cert/generate")
    public Result<Void> generateCertProbe() {
        return Result.ok();
    }
}
