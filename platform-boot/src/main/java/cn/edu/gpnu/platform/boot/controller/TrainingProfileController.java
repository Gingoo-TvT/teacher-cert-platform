package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.dto.TrainingReviewRequest;
import cn.edu.gpnu.platform.business.training.service.TrainingProfileService;
import cn.edu.gpnu.platform.business.training.vo.TrainingOptionsVO;
import cn.edu.gpnu.platform.business.training.vo.TrainingProfileVO;
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

@Tag(name = "专业培养信息")
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingProfileController {

    private final TrainingProfileService trainingProfileService;

    @Operation(summary = "培养信息列表")
    @PreAuthorize("@pms.has('student:view')")
    @DataScope(alias = "training_profile", permission = "student:view")
    @GetMapping
    public Result<PageResult<TrainingProfileVO>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "status", required = false) String status,
                                                      @RequestParam(value = "collegeId", required = false) Long collegeId,
                                                      @RequestParam(value = "assessmentYear", required = false) String assessmentYear) {
        return Result.ok(trainingProfileService.list(keyword, status, collegeId, assessmentYear));
    }

    @Operation(summary = "查询学生培养信息")
    @PreAuthorize("@pms.has('student:view') or @pms.has('training:confirm')")
    @DataScope(alias = "training_profile", permission = "student:view")
    @GetMapping("/{studentId}")
    public Result<TrainingProfileVO> get(@PathVariable Long studentId,
                                         @RequestParam("year") String assessmentYear) {
        return Result.ok(trainingProfileService.get(studentId, assessmentYear));
    }

    @Operation(summary = "培养目标联动选项")
    @PreAuthorize("@pms.has('dict:view') or @pms.has('training:edit') or @pms.has('training:confirm')")
    @GetMapping("/options")
    public Result<TrainingOptionsVO> options(@RequestParam("goal") String trainingGoal,
                                             @RequestParam(value = "segment", required = false) String segment) {
        return Result.ok(trainingProfileService.options(trainingGoal, segment));
    }

    @Operation(summary = "教务维护培养信息")
    @PreAuthorize("@pms.has('training:edit')")
    @AuditLog(bizType = "training", operation = "save")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody TrainingProfileSaveRequest request) {
        return Result.ok(trainingProfileService.save(request, false));
    }

    @Operation(summary = "学生本人确认培养目标")
    @PreAuthorize("@pms.has('training:confirm')")
    @AuditLog(bizType = "training", operation = "confirm")
    @PutMapping
    public Result<Long> confirm(@Valid @RequestBody TrainingProfileSaveRequest request) {
        return Result.ok(trainingProfileService.save(request, true));
    }

    @Operation(summary = "提交培养信息")
    @PreAuthorize("@pms.has('training:edit') or @pms.has('training:confirm')")
    @DataScope(alias = "training_profile", permission = "student:view")
    @AuditLog(bizType = "training", operation = "submit")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        trainingProfileService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "培养信息初审")
    @PreAuthorize("@pms.has('info:firstReview')")
    @DataScope(alias = "training_profile", permission = "info:firstReview")
    @AuditLog(bizType = "training", operation = "firstReview")
    @PostMapping("/{id}/first-review")
    public Result<Void> firstReview(@PathVariable Long id,
                                    @Valid @RequestBody TrainingReviewRequest request) {
        trainingProfileService.firstReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "培养信息复审")
    @PreAuthorize("@pms.has('info:secondReview')")
    @DataScope(alias = "training_profile", permission = "info:secondReview")
    @AuditLog(bizType = "training", operation = "secondReview")
    @PostMapping("/{id}/second-review")
    public Result<Void> secondReview(@PathVariable Long id,
                                     @Valid @RequestBody TrainingReviewRequest request) {
        trainingProfileService.secondReview(id, request);
        return Result.ok();
    }
}
