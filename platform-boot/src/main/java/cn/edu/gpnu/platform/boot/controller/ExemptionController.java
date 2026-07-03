package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.exemption.dto.ExemptionApplyRequest;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionQuery;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionReviewRequest;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionUpdateRequest;
import cn.edu.gpnu.platform.business.exemption.service.ExemptionService;
import cn.edu.gpnu.platform.business.exemption.vo.ExamSubjectVO;
import cn.edu.gpnu.platform.business.exemption.vo.ExemptionRequestVO;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "免考管理")
@RestController
@RequestMapping("/api/exemption")
@RequiredArgsConstructor
public class ExemptionController {

    private final ExemptionService exemptionService;

    @Operation(summary = "学段可免科目")
    @PreAuthorize("@pms.has('dict:view')")
    @GetMapping("/subjects")
    public Result<List<SysDictItem>> subjects(@RequestParam(value = "segment", required = false) String segment) {
        return Result.ok(exemptionService.subjects(segment));
    }

    @Operation(summary = "免考列表")
    @PreAuthorize("@pms.has('student:view') or @pms.has('exemption:apply') or @pms.has('exemption:firstReview') or @pms.has('exemption:secondReview')")
    @DataScope(alias = "exemption_request", permission = "student:view")
    @GetMapping
    public Result<PageResult<ExemptionRequestVO>> list(ExemptionQuery query) {
        return Result.ok(exemptionService.list(query));
    }

    @Operation(summary = "学生免考清单")
    @PreAuthorize("@pms.has('student:view') or @pms.has('exemption:apply')")
    @DataScope(alias = "exemption_request", permission = "student:view")
    @GetMapping("/{studentId}")
    public Result<List<ExemptionRequestVO>> studentRequests(@PathVariable Long studentId,
                                                            @RequestParam("year") String assessmentYear) {
        return Result.ok(exemptionService.studentRequests(studentId, assessmentYear));
    }

    @Operation(summary = "应考科目口径")
    @PreAuthorize("@pms.has('student:view') or @pms.has('exemption:apply')")
    @DataScope(alias = "exemption_request", permission = "student:view")
    @GetMapping("/exam-subjects/{studentId}")
    public Result<List<ExamSubjectVO>> examSubjects(@PathVariable Long studentId,
                                                    @RequestParam("year") String assessmentYear,
                                                    @RequestParam("segment") String teachingSegment) {
        return Result.ok(exemptionService.examSubjects(studentId, assessmentYear, teachingSegment));
    }

    @Operation(summary = "免考申请")
    @PreAuthorize("@pms.has('exemption:apply')")
    @AuditLog(bizType = "exemption", operation = "apply")
    @PostMapping
    public Result<List<Long>> apply(@Valid @RequestBody ExemptionApplyRequest request) {
        return Result.ok(exemptionService.apply(request));
    }

    @Operation(summary = "修改免考申请")
    @PreAuthorize("@pms.has('exemption:apply')")
    @DataScope(alias = "exemption_request", permission = "exemption:apply")
    @AuditLog(bizType = "exemption", operation = "update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ExemptionUpdateRequest request) {
        exemptionService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "上传免考佐证")
    @PreAuthorize("@pms.has('exemption:apply')")
    @AuditLog(bizType = "exemption", operation = "uploadMaterial")
    @PostMapping("/{id}/materials")
    public Result<Void> uploadMaterial(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        exemptionService.uploadMaterial(id, file.getInputStream(), file.getOriginalFilename(), file.getContentType(), file.getSize());
        return Result.ok();
    }

    @Operation(summary = "替换免考佐证")
    @PreAuthorize("@pms.has('exemption:apply')")
    @DataScope(alias = "exemption_material", permission = "exemption:apply")
    @AuditLog(bizType = "exemption", operation = "replaceMaterial")
    @PutMapping("/materials/{materialId}")
    public Result<Void> replaceMaterial(@PathVariable Long materialId,
                                        @RequestParam("file") MultipartFile file) throws IOException {
        exemptionService.replaceMaterial(materialId, file.getInputStream(), file.getOriginalFilename(),
                file.getContentType(), file.getSize());
        return Result.ok();
    }

    @Operation(summary = "删除免考佐证")
    @PreAuthorize("@pms.has('exemption:apply')")
    @DataScope(alias = "exemption_material", permission = "exemption:apply")
    @AuditLog(bizType = "exemption", operation = "deleteMaterial")
    @DeleteMapping("/materials/{materialId}")
    public Result<Void> deleteMaterial(@PathVariable Long materialId) {
        exemptionService.deleteMaterial(materialId);
        return Result.ok();
    }

    @Operation(summary = "免考佐证预览")
    @PreAuthorize("@pms.has('student:view') or @pms.has('exemption:apply') or @pms.has('exemption:firstReview') or @pms.has('exemption:secondReview')")
    @DataScope(alias = "exemption_material", permission = "student:view")
    @GetMapping("/materials/{materialId}/preview")
    public Result<String> previewMaterial(@PathVariable Long materialId) {
        return Result.ok(exemptionService.previewMaterial(materialId));
    }

    @Operation(summary = "提交免考审核")
    @PreAuthorize("@pms.has('exemption:apply')")
    @DataScope(alias = "exemption_request", permission = "exemption:apply")
    @AuditLog(bizType = "exemption", operation = "submit")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        exemptionService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "免考初审")
    @PreAuthorize("@pms.has('exemption:firstReview')")
    @DataScope(alias = "exemption_request", permission = "exemption:firstReview")
    @PostMapping("/{id}/first-review")
    public Result<Void> firstReview(@PathVariable Long id,
                                    @Valid @RequestBody ExemptionReviewRequest request) {
        exemptionService.firstReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "免考复审")
    @PreAuthorize("@pms.has('exemption:secondReview')")
    @DataScope(alias = "exemption_request", permission = "exemption:secondReview")
    @PostMapping("/{id}/second-review")
    public Result<Void> secondReview(@PathVariable Long id,
                                     @Valid @RequestBody ExemptionReviewRequest request) {
        exemptionService.secondReview(id, request);
        return Result.ok();
    }
}
