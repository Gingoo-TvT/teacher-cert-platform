package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.dto.CollegeSaveRequest;
import cn.edu.gpnu.platform.system.service.OrganizationService;
import cn.edu.gpnu.platform.system.vo.CollegeVO;
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

@Tag(name = "学院")
@RestController
@RequestMapping("/api/college")
@RequiredArgsConstructor
public class CollegeController {

    private final OrganizationService organizationService;

    @Operation(summary = "查询学院")
    @PreAuthorize("@pms.has('college:manage') or @pms.has('student:view')")
    @DataScope(alias = "sys_college", permission = "student:view")
    @GetMapping
    public Result<List<CollegeVO>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "status", required = false) Integer status) {
        return Result.ok(organizationService.listColleges(keyword, status));
    }

    @Operation(summary = "新增学院")
    @PreAuthorize("@pms.has('college:manage')")
    @AuditLog(bizType = "college", operation = "create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CollegeSaveRequest request) {
        return Result.ok(organizationService.createCollege(request));
    }

    @Operation(summary = "修改学院")
    @PreAuthorize("@pms.has('college:manage')")
    @AuditLog(bizType = "college", operation = "update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CollegeSaveRequest request) {
        organizationService.updateCollege(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除学院")
    @PreAuthorize("@pms.has('college:manage')")
    @AuditLog(bizType = "college", operation = "delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        organizationService.deleteCollege(id);
        return Result.ok();
    }
}
