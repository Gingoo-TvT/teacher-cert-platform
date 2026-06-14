package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.dto.MajorSaveRequest;
import cn.edu.gpnu.platform.system.dto.MajorTrainingGoalSaveRequest;
import cn.edu.gpnu.platform.system.service.OrganizationService;
import cn.edu.gpnu.platform.system.vo.MajorVO;
import cn.edu.gpnu.platform.system.vo.TrainingGoalVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "专业")
@RestController
@RequestMapping("/api/major")
@RequiredArgsConstructor
public class MajorController {

    private final OrganizationService organizationService;

    @Operation(summary = "查询专业")
    @PreAuthorize("@pms.has('major:manage') or @pms.has('student:view')")
    @DataScope(alias = "sys_major", permission = "student:view")
    @GetMapping
    public Result<List<MajorVO>> list(@RequestParam(value = "collegeId", required = false) Long collegeId,
                                      @RequestParam(value = "yearVersion", required = false) String yearVersion,
                                      @RequestParam(value = "pilotScopeFlag", required = false) Integer pilotScopeFlag,
                                      @RequestParam(value = "status", required = false) Integer status,
                                      @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(organizationService.listMajors(collegeId, yearVersion, pilotScopeFlag, status, keyword));
    }

    @Operation(summary = "查询专业详情")
    @PreAuthorize("@pms.has('major:manage') or @pms.has('student:view')")
    @DataScope(alias = "sys_major", permission = "student:view")
    @GetMapping("/{id}")
    public Result<MajorVO> get(@PathVariable Long id) {
        return Result.ok(organizationService.getMajor(id));
    }

    @Operation(summary = "新增专业")
    @PreAuthorize("@pms.has('major:manage')")
    @AuditLog(bizType = "major", operation = "create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody MajorSaveRequest request) {
        return Result.ok(organizationService.createMajor(request));
    }

    @Operation(summary = "修改专业")
    @PreAuthorize("@pms.has('major:manage')")
    @AuditLog(bizType = "major", operation = "update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MajorSaveRequest request) {
        organizationService.updateMajor(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除专业")
    @PreAuthorize("@pms.has('major:manage')")
    @AuditLog(bizType = "major", operation = "delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        organizationService.deleteMajor(id);
        return Result.ok();
    }

    @Operation(summary = "查询专业培养目标")
    @PreAuthorize("@pms.has('major:manage') or @pms.has('student:view')")
    @DataScope(alias = "major_training_goal", permission = "student:view")
    @GetMapping("/{id}/training-goals")
    public Result<List<TrainingGoalVO>> trainingGoals(@PathVariable Long id) {
        return Result.ok(organizationService.getMajorTrainingGoals(id));
    }

    @Operation(summary = "替换专业培养目标")
    @PreAuthorize("@pms.has('major:manage')")
    @AuditLog(bizType = "major", operation = "replaceTrainingGoals")
    @PutMapping("/{id}/training-goals")
    public Result<Void> replaceTrainingGoals(@PathVariable Long id,
                                             @Valid @RequestBody MajorTrainingGoalSaveRequest request) {
        organizationService.replaceMajorTrainingGoals(id, request.getTrainingGoalCodes());
        return Result.ok();
    }
}
