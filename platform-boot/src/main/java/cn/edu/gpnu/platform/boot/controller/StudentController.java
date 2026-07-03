package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.student.dto.StudentConfirmRequest;
import cn.edu.gpnu.platform.business.student.dto.StudentReviewRequest;
import cn.edu.gpnu.platform.business.student.dto.StudentSaveRequest;
import cn.edu.gpnu.platform.business.student.service.StudentService;
import cn.edu.gpnu.platform.business.student.vo.StudentPlainIdCardVO;
import cn.edu.gpnu.platform.business.student.vo.StudentVO;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
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

@Tag(name = "学生基本信息")
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "学生列表")
    @PreAuthorize("@pms.has('student:view')")
    @DataScope(alias = "student", permission = "student:view")
    @GetMapping
    public Result<PageResult<StudentVO>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "status", required = false) String status,
                                              @RequestParam(value = "collegeId", required = false) Long collegeId) {
        return Result.ok(studentService.list(keyword, status, collegeId, false));
    }

    @Operation(summary = "学生详情")
    @PreAuthorize("@pms.has('student:view')")
    @DataScope(alias = "student", permission = "student:view")
    @GetMapping("/{id}")
    public Result<StudentVO> detail(@PathVariable Long id) {
        return Result.ok(studentService.detail(id, false));
    }

    @Operation(summary = "新增学生")
    @PreAuthorize("@pms.has('student:edit')")
    @AuditLog(bizType = "student", operation = "create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody StudentSaveRequest request) {
        return Result.ok(studentService.create(request));
    }

    @Operation(summary = "批量新增学生")
    @PreAuthorize("@pms.has('student:edit')")
    @AuditLog(bizType = "student", operation = "batchCreate")
    @PostMapping("/batch")
    public Result<List<Long>> batchCreate(@RequestBody List<@Valid StudentSaveRequest> requests) {
        return Result.ok(studentService.batchCreate(requests));
    }

    @Operation(summary = "修改学生")
    @PreAuthorize("@pms.has('student:edit')")
    @DataScope(alias = "student", permission = "student:view")
    @AuditLog(bizType = "student", operation = "update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody StudentSaveRequest request) {
        studentService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除学生")
    @PreAuthorize("@pms.has('student:edit')")
    @DataScope(alias = "student", permission = "student:view")
    @AuditLog(bizType = "student", operation = "delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "学生本人确认/补充")
    @PreAuthorize("@pms.has('student:confirm')")
    @DataScope(alias = "student", permission = "student:confirm")
    @AuditLog(bizType = "student", operation = "confirm")
    @PostMapping("/confirm")
    public Result<StudentVO> confirm(@Valid @RequestBody StudentConfirmRequest request) {
        return Result.ok(studentService.confirm(request));
    }

    @Operation(summary = "提交基本信息")
    @PreAuthorize("@pms.has('student:edit') or @pms.has('student:confirm')")
    @DataScope(alias = "student", permission = "student:view")
    @AuditLog(bizType = "student", operation = "submit")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        studentService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "学生信息初审")
    @PreAuthorize("@pms.has('info:firstReview')")
    @DataScope(alias = "student", permission = "info:firstReview")
    @PostMapping("/{id}/first-review")
    public Result<Void> firstReview(@PathVariable Long id, @Valid @RequestBody StudentReviewRequest request) {
        studentService.firstReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "学生信息复审")
    @PreAuthorize("@pms.has('info:secondReview')")
    @DataScope(alias = "student", permission = "info:secondReview")
    @PostMapping("/{id}/second-review")
    public Result<Void> secondReview(@PathVariable Long id, @Valid @RequestBody StudentReviewRequest request) {
        studentService.secondReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "查看明文证件号")
    @PreAuthorize("@pms.has('exchange:export:sensitive')")
    @DataScope(alias = "student", permission = "student:view")
    @AuditLog(bizType = "student", operation = "plainIdCard")
    @GetMapping("/{id}/id-card")
    public Result<StudentPlainIdCardVO> plainIdCard(@PathVariable Long id,
                                                    @RequestParam(value = "plain", defaultValue = "0") Integer plain) {
        return Result.ok(studentService.plainIdCard(id));
    }
}
