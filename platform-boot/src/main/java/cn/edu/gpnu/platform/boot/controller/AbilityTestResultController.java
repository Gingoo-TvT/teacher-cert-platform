package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestImportRequest;
import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestQuery;
import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestSaveRequest;
import cn.edu.gpnu.platform.business.testresult.service.AbilityTestResultService;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestResultVO;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestValidityVO;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "测试结果")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class AbilityTestResultController {

    private final AbilityTestResultService abilityTestResultService;

    @Operation(summary = "测试结果列表")
    @PreAuthorize("@pms.has('student:view') or @pms.has('test:edit') or @pms.has('test:import') or @pms.has('test:confirm')")
    @DataScope(alias = "ability_test_result", permission = "student:view")
    @GetMapping
    public Result<PageResult<AbilityTestResultVO>> list(AbilityTestQuery query) {
        return Result.ok(abilityTestResultService.list(query));
    }

    @Operation(summary = "学生测试结果")
    @PreAuthorize("@pms.has('student:view') or @pms.has('test:edit')")
    @DataScope(alias = "ability_test_result", permission = "student:view")
    @GetMapping("/{studentId}")
    public Result<AbilityTestResultVO> get(@PathVariable Long studentId,
                                           @RequestParam("year") String assessmentYear,
                                           @RequestParam(value = "segment", required = false) String teachingSegment) {
        return Result.ok(abilityTestResultService.get(studentId, assessmentYear, teachingSegment));
    }

    @Operation(summary = "录入测试结果")
    @PreAuthorize("@pms.has('test:edit')")
    @AuditLog(bizType = "abilityTest", operation = "save")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody AbilityTestSaveRequest request) {
        return Result.ok(abilityTestResultService.save(request));
    }

    @Operation(summary = "修改测试结果")
    @PreAuthorize("@pms.has('test:edit')")
    @AuditLog(bizType = "abilityTest", operation = "update")
    @PutMapping
    public Result<Long> update(@Valid @RequestBody AbilityTestSaveRequest request) {
        return Result.ok(abilityTestResultService.save(request));
    }

    @Operation(summary = "批量导入测试结果")
    @PreAuthorize("@pms.has('test:import')")
    @AuditLog(bizType = "abilityTest", operation = "import")
    @PostMapping("/import")
    public Result<List<Long>> importRows(@Valid @RequestBody AbilityTestImportRequest request) {
        return Result.ok(abilityTestResultService.importRows(request));
    }

    @Operation(summary = "文件导入测试结果")
    @PreAuthorize("@pms.has('test:import')")
    @AuditLog(bizType = "abilityTest", operation = "importFile")
    @PostMapping("/import-file")
    public Result<List<Long>> importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(abilityTestResultService.importFile(file.getInputStream(), file.getOriginalFilename()));
    }

    @Operation(summary = "确认锁定测试结果")
    @PreAuthorize("@pms.has('test:confirm')")
    @DataScope(alias = "ability_test_result", permission = "test:confirm")
    @AuditLog(bizType = "abilityTest", operation = "confirm")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        abilityTestResultService.confirm(id);
        return Result.ok();
    }

    @Operation(summary = "测试结论有效性")
    @PreAuthorize("@pms.has('student:view') or @pms.has('test:edit') or @pms.has('cert:generate')")
    @DataScope(alias = "ability_test_result", permission = "student:view")
    @GetMapping("/{studentId}/validity")
    public Result<AbilityTestValidityVO> validity(@PathVariable Long studentId,
                                                  @RequestParam("year") String assessmentYear) {
        return Result.ok(abilityTestResultService.validity(studentId, assessmentYear));
    }
}
